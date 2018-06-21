package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"reflect"
	"strconv"
	"strings"

	_ "expvar"

	_ "github.com/lib/pq"

	"github.com/gorilla/mux"
	"github.com/spf13/viper"
)

// The following Null* type code was taken from or informed by the blog at
// https://medium.com/aubergine-solutions/how-i-handled-null-possible-values-from-database-rows-in-golang-521fb0ee267.

// NullInt64 is an alias of the sql.NullInt64. Having an alias allows us to
// extend the types, which we can't do without the alias because sql.NullInt64
// is defined in another package.
type NullInt64 sql.NullInt64

// Scan implements the Scanner interface for our NullInt64 alias.
func (i *NullInt64) Scan(value interface{}) error {
	var (
		valid bool
		n     sql.NullInt64
		err   error
	)

	if err = n.Scan(value); err != nil {
		return err
	}

	if reflect.TypeOf(value) != nil {
		valid = true
	}

	*i = NullInt64{i.Int64, valid}
	return nil
}

// MarshalJSON implements the json.Marshaler interface for our NullInt64 alias.
func (i *NullInt64) MarshalJSON() ([]byte, error) {
	if !i.Valid {
		return []byte("null"), nil
	}
	return json.Marshal(i.Int64)
}

// Job is an entry from the jobs table in the database. It contains a minimal
// set of fields.
type Job struct {
	ID             string    `json:"id"`
	AppID          string    `json:"app_id"`
	UserID         string    `json:"user_id"`
	Status         string    `json:"status"`
	Description    string    `json:"description"`
	Name           string    `json:"name"`
	PlannedEndDate NullInt64 `json:"planned_end_date,omitempty"`
}

// JobList is a list of Jobs. Duh.
type JobList struct {
	Jobs []Job `json:"jobs"`
}

const listJobsQuery = `
SELECT id,
       app_id,
       user_id,
       status,
       planned_end_date,
       job_description as description,
       job_name as name
  FROM jobs
 WHERE status = $1
   AND planned_end_date < NOW()`

func listJobs(ctx context.Context, db *sql.DB, status string) (*JobList, error) {
	rows, err := db.QueryContext(ctx, listJobsQuery, status)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	jl := &JobList{
		Jobs: []Job{},
	}

	for rows.Next() {
		var j Job
		err = rows.Scan(
			&j.ID,
			&j.AppID,
			&j.UserID,
			&j.Status,
			&j.PlannedEndDate,
		)
		if err != nil {
			return nil, err
		}
		jl.Jobs = append(jl.Jobs, j)
	}
	return jl, nil
}

const getJobByIDQuery = `
SELECT id,
       app_id,
       user_id,
       status,
       planned_end_date,
       job_description as description,
       job_name as name
  FROM jobs
 WHERE id = $1`

func getJobByID(ctx context.Context, db *sql.DB, id string) (*Job, error) {
	var (
		j   Job
		err error
	)

	row := db.QueryRowContext(ctx, getJobByIDQuery, id)

	if err = row.Scan(
		&j.ID,
		&j.AppID,
		&j.UserID,
		&j.Status,
		&j.PlannedEndDate,
		&j.Description,
		&j.Name,
	); err != nil {
		return nil, err
	}

	return &j, err
}

const updateJobTemplate = `
UPDATE ONLY jobs
  SET %s
WHERE id = $1
RETURNING id, app_id, user_id, status, planned_end_date, job_description, job_name`

func updateJob(ctx context.Context, db *sql.DB, id string, patch map[string]string) (*Job, error) {
	var err error

	setstring := "%s = '%s'"
	sets := []string{}

	for k, v := range patch {
		sets = append(sets, fmt.Sprintf(setstring, k, v))
	}

	if len(sets) == 0 {
		return nil, errors.New("nothing in patch")
	}

	fullupdate := fmt.Sprintf(updateJobTemplate, strings.Join(sets, ", "))

	j := &Job{}
	row := db.QueryRowContext(ctx, fullupdate, id)
	if err = row.Scan(
		&j.ID,
		&j.AppID,
		&j.UserID,
		&j.Status,
		&j.PlannedEndDate,
		&j.Description,
		&j.Name,
	); err != nil {
		return nil, err
	}

	return j, nil
}

func main() {
	var (
		err        error
		listenPort = flag.Int("listen-port", 60000, "The port to listen on.")
		sslCert    = flag.String("ssl-cert", "", "Path to the SSL .crt file.")
		sslKey     = flag.String("ssl-key", "", "Path to the SSL .key file.")
	)

	ctx := context.Background()

	flag.Parse()

	useSSL := false
	if *sslCert != "" || *sslKey != "" {
		if *sslCert == "" {
			log.Fatal("--ssl-cert is required with --ssl-key.")
		}

		if *sslKey == "" {
			log.Fatal("--ssl-key is required with --ssl-cert.")
		}
		useSSL = true
	}

	viper.SetConfigType("yaml")
	viper.SetConfigName("jobservices")
	viper.AddConfigPath("/etc/iplant/de/")
	viper.AddConfigPath("$HOME/.jobservices")
	viper.AddConfigPath(".")
	if err = viper.ReadInConfig(); err != nil {
		log.Fatal(err)
	}

	dbURI := viper.GetString("db.uri")
	dbparsed, err := url.Parse(dbURI)
	if err != nil {
		log.Fatal(err)
	}
	if dbparsed.Scheme == "postgresql" {
		dbparsed.Scheme = "postgres"
	}
	dbURI = dbparsed.String()

	db, err := sql.Open("postgres", dbURI)
	if err != nil {
		log.Fatal(err)
	}

	if err = db.Ping(); err != nil {
		log.Fatal(err)
	}

	router := mux.NewRouter()

	router.HandleFunc("/expired/{status}", func(w http.ResponseWriter, r *http.Request) {
		validStatuses := map[string]bool{
			"Completed": true,
			"Failed":    true,
			"Submitted": true,
			"Queued":    true,
			"Running":   true,
			"Canceled":  true,
		}

		vars := mux.Vars(r)
		status := strings.Title(strings.ToLower(vars["status"]))
		if _, ok := validStatuses[status]; !ok {
			http.Error(w, fmt.Sprintf("unknown status %s", status), http.StatusBadRequest)
			return
		}

		list, err := listJobs(ctx, db, status)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set(http.CanonicalHeaderKey("content-type"), "application/json")

		if err = json.NewEncoder(w).Encode(list); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
	}).Methods("GET")

	idPath := "/id/{id:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}"

	router.HandleFunc(idPath, func(w http.ResponseWriter, r *http.Request) {
		vars := mux.Vars(r)
		id := vars["id"]

		job, err := getJobByID(ctx, db, id)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set(http.CanonicalHeaderKey("content-type"), "application/json")

		if err = json.NewEncoder(w).Encode(job); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
	}).Methods("GET")

	router.HandleFunc(idPath, func(w http.ResponseWriter, r *http.Request) {
		var (
			ok  bool
			err error
		)

		vars := mux.Vars(r)
		id := vars["id"]
		defer r.Body.Close()

		jobpatch := make(map[string]interface{})

		if err = json.NewDecoder(r.Body).Decode(&jobpatch); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		dbpatch := map[string]string{}

		if _, ok = jobpatch["status"]; ok {
			dbpatch["status"] = jobpatch["status"].(string)
		}

		if _, ok = jobpatch["planned_end_date"]; ok {
			dbpatch["planned_end_date"] = strconv.FormatInt(jobpatch["planned_end_date"].(int64), 10)
		}

		if _, ok = jobpatch["description"]; ok {
			dbpatch["job_description"] = jobpatch["description"].(string)
		}

		if _, ok = jobpatch["name"]; ok {
			dbpatch["job_name"] = jobpatch["name"].(string)
		}

		job, err := updateJob(ctx, db, id, dbpatch)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		if err = json.NewEncoder(w).Encode(job); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}

	}).Methods("PATCH")

	router.Handle("/debug/vars", http.DefaultServeMux)

	addr := fmt.Sprintf(":%d", *listenPort)
	if useSSL {
		log.Fatal(http.ListenAndServeTLS(addr, *sslCert, *sslKey, router))
	} else {
		log.Fatal(http.ListenAndServe(addr, router))
	}

}

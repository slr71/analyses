package main

import (
	"context"
	"database/sql"
	"database/sql/driver"
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

	"github.com/lib/pq"

	"github.com/gorilla/mux"
	"github.com/spf13/viper"
)

// The following Null* type code was taken from or informed by the blog at
// https://medium.com/aubergine-solutions/how-i-handled-null-possible-values-from-database-rows-in-golang-521fb0ee267.
// and
// https://stackoverflow.com/questions/24564619/nullable-time-time-in-golang

// NullTime is an alias of pq.NullTime, which allows us to extend the type by
// implementing custom JSON marshalling logic.
type NullTime pq.NullTime

// Scan implements the Scanner interface for the NullTime alias. Basically just
// delegates to the Scan() implementation for pq.NullTime.
func (t *NullTime) Scan(value interface{}) error {
	var (
		valid bool
		nt    pq.NullTime
		err   error
	)

	if err = nt.Scan(value); err != nil {
		return err
	}

	if reflect.TypeOf(value) != nil {
		valid = true
	}

	*t = NullTime{nt.Time, valid}
	return nil
}

// Value implements the Valuer interface for the NullTime alias. Needed for the
// pq driver.
func (t NullTime) Value() (driver.Value, error) {
	if !t.Valid {
		return nil, nil
	}
	return t.Time, nil
}

// MarshalJSON implements the json.Marshaler interface for our NullTime alias.
// We're using this to convert timestamps to int64s containing the milliseconds
// since the epoch.
func (t *NullTime) MarshalJSON() ([]byte, error) {
	if !t.Valid {
		return []byte("null"), nil
	}
	return []byte(strconv.FormatInt(t.Time.UnixNano()/1000000, 10)), nil
}

// Job is an entry from the jobs table in the database. It contains a minimal
// set of fields.
type Job struct {
	ID             string   `json:"id"`
	AppID          string   `json:"app_id"`
	UserID         string   `json:"user_id"`
	Username       string   `json:"username"`
	Status         string   `json:"status"`
	Description    string   `json:"description"`
	Name           string   `json:"name"`
	ResultFolder   string   `json:"result_folder"`
	StartDate      NullTime `json:"start_date"`
	PlannedEndDate NullTime `json:"planned_end_date,omitempty"`
}

// JobList is a list of Jobs. Duh.
type JobList struct {
	Jobs []Job `json:"jobs"`
}

const listJobsQuery = `
SELECT j.id,
       j.app_id,
       j.user_id,
       u.username,
       j.status,
       j.job_description as description,
       j.job_name as name,
       j.result_folder_path as result_folder,
       j.start_date,
       j.planned_end_date
  FROM jobs j
  JOIN users u
    ON j.user_id = u.id
 WHERE j.status = $1
   AND j.planned_end_date <= NOW()`

const jobsToKillInFutureQuery = `
SELECT j.id,
       j.app_id,
       j.user_id,
       u.username,
       j.status,
       j.job_description as description,
       j.job_name as name,
       j.result_folder_path as result_folder,
       j.start_date,
       j.planned_end_date
  FROM jobs j
  JOIN users u
    ON j.user_id = u.id
 WHERE j.status = $1
   AND NOW() < j.planned_end_date
	 AND j.planned_end_date <= NOW() + interval '%d minutes'`

func getJobList(ctx context.Context, db *sql.DB, query, status string) (*JobList, error) {
	rows, err := db.QueryContext(ctx, query, status)
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
			&j.Username,
			&j.Status,
			&j.Description,
			&j.Name,
			&j.ResultFolder,
			&j.StartDate,
			&j.PlannedEndDate,
		)
		if err != nil {
			return nil, err
		}
		jl.Jobs = append(jl.Jobs, j)
	}
	return jl, nil
}

func listJobs(ctx context.Context, db *sql.DB, status string) (*JobList, error) {
	return getJobList(ctx, db, listJobsQuery, status)
}

func listJobsToKillInFuture(ctx context.Context, db *sql.DB, status string, interval int64) (*JobList, error) {
	return getJobList(ctx, db, fmt.Sprintf(jobsToKillInFutureQuery, interval), status)
}

const getJobByIDQuery = `
SELECT j.id,
       j.app_id,
       j.user_id,
       u.username,
       j.status,
       j.job_description as description,
       j.job_name as name,
       j.result_folder_path as result_folder,
       j.start_date,
       j.planned_end_date
  FROM jobs j
  JOIN users u
    ON j.user_id = u.id
 WHERE j.id = $1`

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
		&j.Username,
		&j.Status,
		&j.Description,
		&j.Name,
		&j.ResultFolder,
		&j.StartDate,
		&j.PlannedEndDate,
	); err != nil {
		return nil, err
	}

	return &j, err
}

const updateJobTemplate = `
UPDATE ONLY jobs
  SET %s
WHERE id = $1`

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

	if _, err = db.ExecContext(ctx, fullupdate, id); err != nil {
		return nil, err
	}

	j, err := getJobByID(ctx, db, id)
	if err != nil {
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

	router.HandleFunc("/expires-in/{minutes:[0-9]+}/{status}", func(w http.ResponseWriter, r *http.Request) {
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

		minutes, err := strconv.ParseInt(vars["minutes"], 10, 64)
		if err != nil {
			http.Error(w, fmt.Sprintf("can't parse %s as an integer", vars["minutes"]), http.StatusBadRequest)
			return
		}

		list, err := listJobsToKillInFuture(ctx, db, status, minutes)
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

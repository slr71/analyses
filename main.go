package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"reflect"
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
       planned_end_date
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
       planned_end_date
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
	); err != nil {
		return nil, err
	}

	return &j, err
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

	router.HandleFunc("/id/{id:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}", func(w http.ResponseWriter, r *http.Request) {
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

	router.Handle("/debug/vars", http.DefaultServeMux)

	addr := fmt.Sprintf(":%d", *listenPort)
	if useSSL {
		log.Fatal(http.ListenAndServeTLS(addr, *sslCert, *sslKey, router))
	} else {
		log.Fatal(http.ListenAndServe(addr, router))
	}

}

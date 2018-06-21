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
	"strings"

	_ "expvar"

	_ "github.com/lib/pq"

	"github.com/gorilla/mux"
	"github.com/spf13/viper"
)

// Job is an entry from the jobs table in the database. It contains a minimal
// set of fields.
type Job struct {
	ID             string
	AppID          string
	UserID         string
	Status         string
	PlannedEndDate int64
}

// JobList is a list of Jobs. Duh.
type JobList struct {
	Jobs []Job
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

	router.HandleFunc("/analyses/expired/{status}", func(w http.ResponseWriter, r *http.Request) {
		validStatuses := map[string]bool{
			"Completed": true,
			"Failed":    true,
			"Submitted": true,
			"Queued":    true,
			"Running":   true,
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

		json.NewEncoder(w).Encode(list)
	}).Methods("GET")

	router.Handle("/debug/vars", http.DefaultServeMux)

	addr := fmt.Sprintf(":%d", *listenPort)
	if useSSL {
		log.Fatal(http.ListenAndServeTLS(addr, *sslCert, *sslKey, router))
	} else {
		log.Fatal(http.ListenAndServe(addr, router))
	}

}

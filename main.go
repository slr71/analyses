package main

import (
	"database/sql"
	"flag"
	"fmt"
	"log"
	"net/http"

	"github.com/spf13/viper"
)

func main() {
	var (
		err        error
		listenPort = flag.Int("listen-port", 60000, "The port to listen on.")
		sslCert    = flag.String("ssl-cert", "", "Path to the SSL .crt file.")
		sslKey     = flag.String("ssl-key", "", "Path to the SSL .key file.")
	)

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

	db, err := sql.Open("postgres", dbURI)
	if err != nil {
		log.Fatal(err)
	}

	if err = db.Ping(); err != nil {
		log.Fatal(err)
	}

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {

	})

	addr := fmt.Sprintf(":%d", *listenPort)
	if useSSL {
		log.Fatal(http.ListenAndServeTLS(addr, *sslCert, *sslKey, nil))
	} else {
		log.Fatal(http.ListenAndServe(addr, nil))
	}

}

package main

import (
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"onetune/server/internal/music"
	"onetune/server/internal/resolve"
	"onetune/server/internal/web"
)

func main() {
	providers := []music.Provider{music.NewSpotify(), music.NewApple(), music.NewDeezer(), music.NewYouTubeMusic()}
	if id, secret := os.Getenv("TIDAL_CLIENT_ID"), os.Getenv("TIDAL_CLIENT_SECRET"); id != "" && secret != "" {
		country := os.Getenv("TIDAL_COUNTRY_CODE")
		if country == "" {
			country = "US"
		}
		providers = append(providers, music.NewTidal(id, secret, country))
	} else {
		log.Print("TIDAL_CLIENT_ID/TIDAL_CLIENT_SECRET not set: Tidal disabled")
	}

	domain := os.Getenv("DOMAIN")
	if domain == "" {
		log.Fatal("DOMAIN is required")
	}
	handler := web.Handler(resolve.New(providers), web.Config{
		BaseURL:            "https://" + domain,
		AndroidPackage:     os.Getenv("ANDROID_PACKAGE"),
		AndroidCertSHA256s: splitList(os.Getenv("ANDROID_CERT_SHA256")),
	})

	address := os.Getenv("ADDRESS")
	if address == "" {
		address = ":8080"
	}
	server := &http.Server{
		Addr:              address,
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	log.Printf("listening on %s for %s", address, domain)
	log.Fatal(server.ListenAndServe())
}

func splitList(value string) []string {
	var items []string
	for _, item := range strings.Split(value, ",") {
		if item = strings.TrimSpace(item); item != "" {
			items = append(items, item)
		}
	}
	return items
}

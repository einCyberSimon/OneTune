package web

import (
	"crypto/rand"
	_ "embed"
	"encoding/base64"
	"encoding/json"
	"errors"
	"html/template"
	"log"
	"net/http"
	"strings"

	"onetune/server/internal/music"
	"onetune/server/internal/resolve"
)

//go:embed resolve.html
var resolvePage string

var pageTemplate = template.Must(template.New("resolve").Parse(resolvePage))

type Config struct {
	BaseURL            string
	AndroidPackage     string
	AndroidCertSHA256s []string
}

type pageData struct {
	Result  resolve.Result
	PageURL string
	Nonce   string
}

func Handler(resolver *resolve.Resolver, config Config) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
	mux.HandleFunc("GET /.well-known/assetlinks.json", assetLinks(config))
	mux.HandleFunc("GET /{platform}/{type}/{id}", func(w http.ResponseWriter, r *http.Request) {
		ref := music.Ref{Platform: r.PathValue("platform"), Type: r.PathValue("type"), ID: r.PathValue("id")}
		if !resolver.ValidRef(ref) {
			http.NotFound(w, r)
			return
		}
		result, err := resolver.Resolve(r.Context(), ref)
		if err != nil {
			status := http.StatusBadGateway
			switch {
			case errors.Is(err, music.ErrNotFound):
				status = http.StatusNotFound
			case errors.Is(err, resolve.ErrBusy):
				status = http.StatusServiceUnavailable
				w.Header().Set("Retry-After", "5")
			default:
				log.Printf("resolve on %s failed: %v", ref.Platform, err)
			}
			http.Error(w, "could not resolve this track", status)
			return
		}
		w.Header().Set("Cache-Control", "public, max-age=3600")
		w.Header().Set("Vary", "Accept")
		if strings.Contains(r.Header.Get("Accept"), "application/json") {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(result)
			return
		}
		renderPage(w, pageData{Result: result, PageURL: config.BaseURL + r.URL.Path})
	})
	return mux
}

func renderPage(w http.ResponseWriter, data pageData) {
	nonce := make([]byte, 16)
	_, _ = rand.Read(nonce)
	data.Nonce = base64.StdEncoding.EncodeToString(nonce)
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Content-Security-Policy", "default-src 'none'; img-src https:; style-src 'unsafe-inline'; script-src 'nonce-"+data.Nonce+"'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
	w.Header().Set("Referrer-Policy", "no-referrer")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	if err := pageTemplate.Execute(w, data); err != nil {
		log.Printf("render failed: %v", err)
	}
}

func assetLinks(config Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if config.AndroidPackage == "" || len(config.AndroidCertSHA256s) == 0 {
			http.NotFound(w, r)
			return
		}
		statement := []map[string]any{{
			"relation": []string{"delegate_permission/common.handle_all_urls"},
			"target": map[string]any{
				"namespace":                "android_app",
				"package_name":             config.AndroidPackage,
				"sha256_cert_fingerprints": config.AndroidCertSHA256s,
			},
		}}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(statement)
	}
}

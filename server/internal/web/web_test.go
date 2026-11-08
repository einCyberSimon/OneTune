package web

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"onetune/server/internal/music"
	"onetune/server/internal/music/musictest"
	"onetune/server/internal/resolve"
)

func newHandler(t *testing.T, config Config, entity music.Entity) http.Handler {
	t.Helper()
	providers := []music.Provider{
		&musictest.Fake{PlatformName: "src", Entity: entity},
		&musictest.Fake{PlatformName: "other", Found: music.Found{URL: "https://other.example/found"}},
	}
	return Handler(resolve.New(providers), config)
}

func get(handler http.Handler, path, accept string) *httptest.ResponseRecorder {
	request := httptest.NewRequest(http.MethodGet, path, nil)
	if accept != "" {
		request.Header.Set("Accept", accept)
	}
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)
	return recorder
}

func TestResolvePageEscapesUpstreamText(t *testing.T) {
	handler := newHandler(t, Config{BaseURL: "https://example.org"}, music.Entity{Title: "<script>alert(1)</script>", Artist: `A"B`})
	response := get(handler, "/src/track/abc", "text/html")
	body := response.Body.String()

	if response.Code != http.StatusOK {
		t.Fatalf("status %d", response.Code)
	}
	if strings.Contains(body, "<script>alert(1)") {
		t.Error("title was not escaped")
	}
	if !strings.Contains(body, `href="https://other.example/found"`) || !strings.Contains(body, "https://example.org/src/track/abc") {
		t.Error("page is missing the target link or the canonical URL")
	}
}

func TestResolveResponseHeaders(t *testing.T) {
	handler := newHandler(t, Config{BaseURL: "https://example.org"}, music.Entity{Title: "T", Artist: "A"})
	response := get(handler, "/src/track/abc", "")

	if got := response.Header().Get("Vary"); got != "Accept" {
		t.Errorf("Vary %q: HTML and JSON share a URL, caches must key on Accept", got)
	}
	policy := response.Header().Get("Content-Security-Policy")
	for _, directive := range []string{"default-src 'none'", "frame-ancestors 'none'", "script-src 'nonce-"} {
		if !strings.Contains(policy, directive) {
			t.Errorf("CSP %q lacks %q", policy, directive)
		}
	}
	first := get(handler, "/src/track/abc", "").Header().Get("Content-Security-Policy")
	second := get(handler, "/src/track/abc", "").Header().Get("Content-Security-Policy")
	if first == second {
		t.Error("CSP nonce must change per request")
	}
}

func TestResolveJSON(t *testing.T) {
	handler := newHandler(t, Config{}, music.Entity{Title: "T", Artist: "A"})
	response := get(handler, "/src/track/abc", "application/json")

	if got := response.Header().Get("Content-Type"); got != "application/json" {
		t.Fatalf("content type %q", got)
	}
	var result resolve.Result
	if err := json.Unmarshal(response.Body.Bytes(), &result); err != nil {
		t.Fatal(err)
	}
	if result.Entity.Title != "T" || len(result.Links) != 2 {
		t.Errorf("unexpected result %+v", result)
	}
}

func TestRejectsMalformedPaths(t *testing.T) {
	handler := newHandler(t, Config{}, music.Entity{Title: "T", Artist: "A"})
	for _, path := range []string{
		"/nope/track/abc",
		"/src/album/abc",
		"/src/track/a%2Fb",
		"/src/track/a%3Fb",
		"/src/track/a%20b",
		"/src/track/a%0d%0ab",
		"/src/track/..%2F..%2Fetc",
	} {
		if code := get(handler, path, "").Code; code != http.StatusNotFound {
			t.Errorf("%s: status %d, want 404", path, code)
		}
	}
}

func TestUnknownTrackIs404(t *testing.T) {
	providers := []music.Provider{&musictest.Fake{PlatformName: "src", LookupErr: music.ErrNotFound}}
	handler := Handler(resolve.New(providers), Config{})
	if code := get(handler, "/src/track/abc", "").Code; code != http.StatusNotFound {
		t.Errorf("status %d", code)
	}
}

func TestAssetLinks(t *testing.T) {
	configured := newHandler(t, Config{AndroidPackage: "de.example.app", AndroidCertSHA256s: []string{"AA:BB"}}, music.Entity{})
	response := get(configured, "/.well-known/assetlinks.json", "")
	if response.Code != http.StatusOK || !strings.Contains(response.Body.String(), `"package_name":"de.example.app"`) || !strings.Contains(response.Body.String(), "AA:BB") {
		t.Errorf("status %d body %s", response.Code, response.Body)
	}

	unconfigured := newHandler(t, Config{}, music.Entity{})
	if code := get(unconfigured, "/.well-known/assetlinks.json", "").Code; code != http.StatusNotFound {
		t.Errorf("unconfigured status %d, want 404", code)
	}
}

func TestHealthz(t *testing.T) {
	if code := get(newHandler(t, Config{}, music.Entity{}), "/healthz", "").Code; code != http.StatusNoContent {
		t.Errorf("status %d", code)
	}
}

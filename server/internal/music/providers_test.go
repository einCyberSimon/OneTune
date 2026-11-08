package music

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync/atomic"
	"testing"
)

// Fixtures in testdata/ are recorded by scripts/record-fixtures.sh, except the
// tidal_* files, which are hand-written from the API documentation.

var (
	getLuckyFromSpotify = Entity{Title: "Get Lucky (feat. Pharrell Williams and Nile Rodgers)", Artist: "Daft Punk, Pharrell Williams, Nile Rodgers", DurationMs: 370000}
	getLuckyRadioEdit   = Entity{Title: "Get Lucky (Radio Edit - feat. Pharrell Williams and Nile Rodgers)", Artist: "Daft Punk", DurationMs: 249000}
)

// fixtureServer serves testdata files by exact URL path.
func fixtureServer(t *testing.T, routes map[string]string) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		file, ok := routes[r.URL.Path]
		if !ok {
			http.NotFound(w, r)
			return
		}
		data, err := os.ReadFile("testdata/" + file)
		if err != nil {
			t.Error(err)
		}
		_, _ = w.Write(data)
	}))
	t.Cleanup(server.Close)
	return server
}

func unlimited() *upstream { return newUpstream(1e6, 1000) }

func TestDeezer(t *testing.T) {
	server := fixtureServer(t, map[string]string{
		"/track/67238735":          "deezer_track.json",
		"/track/isrc:USQX91300108": "deezer_track.json",
		"/search/track":            "deezer_search.json",
		"/track/isrc:AAAAAAAAAAAA": "missing",
	})
	deezer := &Deezer{upstream: unlimited(), apiBase: server.URL}
	ctx := context.Background()

	entity, err := deezer.Lookup(ctx, "67238735")
	if err != nil || entity.ISRC != "USQX91300108" || entity.Artist != "Daft Punk" || entity.DurationMs != 367000 {
		t.Fatalf("lookup: %+v, %v", entity, err)
	}

	found, err := deezer.Find(ctx, Entity{ISRC: "USQX91300108"})
	if err != nil || found.URL != "https://www.deezer.com/track/67238735" {
		t.Fatalf("find by ISRC: %+v, %v", found, err)
	}

	// The search lists the 248s radio edit first; duration must rule it out.
	found, err = deezer.Find(ctx, getLuckyFromSpotify)
	if err != nil || found.URL != "https://www.deezer.com/track/67238735" || found.ISRC != "USQX91300108" {
		t.Fatalf("find by metadata: %+v, %v", found, err)
	}

	if _, err := deezer.Find(ctx, Entity{Title: "Unrelated Song", Artist: "Nobody"}); !errors.Is(err, ErrNotFound) {
		t.Fatalf("unrelated song: %v", err)
	}
}

func TestApple(t *testing.T) {
	server := fixtureServer(t, map[string]string{
		"/lookup": "apple_lookup.json",
		"/search": "apple_search.json",
	})
	apple := &Apple{upstream: unlimited(), apiBase: server.URL}
	ctx := context.Background()

	entity, err := apple.Lookup(ctx, "617154366")
	if err != nil || entity.Title != "Get Lucky" || entity.DurationMs != 369629 || !strings.Contains(entity.ArtworkURL, "600x600") {
		t.Fatalf("lookup: %+v, %v", entity, err)
	}

	// The 632s remix and the drumless edition must not win over the original.
	found, err := apple.Find(ctx, getLuckyFromSpotify)
	if err != nil || found.URL != "https://music.apple.com/us/song/617154366" {
		t.Fatalf("find: %+v, %v", found, err)
	}

	if _, err := apple.Find(ctx, Entity{Title: "Unrelated Song", Artist: "Nobody"}); !errors.Is(err, ErrNotFound) {
		t.Fatalf("unrelated song: %v", err)
	}
}

func TestSpotify(t *testing.T) {
	server := fixtureServer(t, map[string]string{"/track/69kOkLUCkxIZYexIgSG8rq": "spotify_track.html"})
	spotify := &Spotify{upstream: unlimited(), pageBase: server.URL}
	ctx := context.Background()

	entity, err := spotify.Lookup(ctx, "69kOkLUCkxIZYexIgSG8rq")
	if err != nil {
		t.Fatal(err)
	}
	if entity.Title != getLuckyFromSpotify.Title || entity.Artist != getLuckyFromSpotify.Artist || entity.DurationMs != 370000 || entity.ArtworkURL == "" {
		t.Fatalf("lookup: %+v", entity)
	}

	if _, err := spotify.Lookup(ctx, "AAAAAAAAAAAAAAAAAAAAAA"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("unknown track: %v", err)
	}

	found, err := spotify.Find(ctx, entity)
	if err != nil || !found.Search || !strings.HasPrefix(found.URL, "https://open.spotify.com/search/") {
		t.Fatalf("find: %+v, %v", found, err)
	}
}

func TestYouTubeMusic(t *testing.T) {
	server := fixtureServer(t, map[string]string{
		"/youtubei/v1/player": "ytmusic_player.json",
		"/youtubei/v1/search": "ytmusic_search.json",
	})
	youtube := &YouTubeMusic{upstream: unlimited(), apiBase: server.URL}
	ctx := context.Background()

	entity, err := youtube.Lookup(ctx, "4D7u5KF7SP8")
	if err != nil || entity.Artist != "Daft Punk, Pharrell Williams & Nile Rodgers" || entity.DurationMs != 370000 || entity.ArtworkURL == "" {
		t.Fatalf("lookup: %+v, %v", entity, err)
	}

	found, err := youtube.Find(ctx, getLuckyFromSpotify)
	if err != nil || found.URL != "https://music.youtube.com/watch?v=4D7u5KF7SP8" {
		t.Fatalf("album version: %+v, %v", found, err)
	}

	found, err = youtube.Find(ctx, getLuckyRadioEdit)
	if err != nil || found.URL != "https://music.youtube.com/watch?v=Rgrt_8mXrK8" {
		t.Fatalf("radio edit: %+v, %v", found, err)
	}

	if _, err := youtube.Find(ctx, Entity{Title: "Unrelated Song", Artist: "Nobody"}); !errors.Is(err, ErrNotFound) {
		t.Fatalf("unrelated song: %v", err)
	}
}

func TestTidal(t *testing.T) {
	var tokenRequests atomic.Int32
	read := func(name string) []byte {
		data, err := os.ReadFile("testdata/" + name)
		if err != nil {
			t.Fatal(err)
		}
		return data
	}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /token", func(w http.ResponseWriter, r *http.Request) {
		tokenRequests.Add(1)
		if _, _, ok := r.BasicAuth(); !ok {
			http.Error(w, "missing client credentials", http.StatusUnauthorized)
			return
		}
		_, _ = w.Write(read("tidal_token.json"))
	})
	mux.HandleFunc("GET /v2/tracks/190405576", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer test-token" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		_, _ = w.Write(read("tidal_track.json"))
	})
	mux.HandleFunc("GET /v2/tracks", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer test-token" || r.URL.Query().Get("filter[isrc]") != "USQX91300108" {
			http.NotFound(w, r)
			return
		}
		_, _ = w.Write(read("tidal_isrc.json"))
	})
	mux.HandleFunc("GET /v2/searchResults", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer test-token" || r.URL.Query().Get("filter[query]") == "" {
			http.NotFound(w, r)
			return
		}
		_, _ = w.Write(read("tidal_search.json"))
	})
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)

	tidal := NewTidal("id", "secret", "US")
	tidal.upstream = unlimited()
	tidal.authURL = server.URL + "/token"
	tidal.apiBase = server.URL + "/v2"
	ctx := context.Background()

	// "included" lists the artists as Nile, Pharrell, Daft Punk; the track's own order must win.
	entity, err := tidal.Lookup(ctx, "190405576")
	if err != nil || entity.Title != "Get Lucky" || entity.ISRC != "USQX91300108" || entity.Artist != "Daft Punk, Pharrell Williams, Nile Rodgers" || entity.DurationMs != 367000 {
		t.Fatalf("lookup: %+v, %v", entity, err)
	}

	// The first of nine releases has no availability; the first streamable one is preferred.
	found, err := tidal.Find(ctx, Entity{ISRC: "USQX91300108"})
	if err != nil || found.URL != "https://tidal.com/track/183655299/u" {
		t.Fatalf("find by ISRC: %+v, %v", found, err)
	}

	// Search also lists a different "Go Back" by other artists, which must not match.
	found, err = tidal.Find(ctx, Entity{Title: "Go Back", Artist: "Velogic", DurationMs: 156000})
	if err != nil || found.URL != "https://tidal.com/track/179744049/u" || found.ISRC != "USLZJ2137213" {
		t.Fatalf("find by search: %+v, %v", found, err)
	}

	if _, err := tidal.Find(ctx, Entity{Title: "Unrelated Song", Artist: "Nobody"}); !errors.Is(err, ErrNotFound) {
		t.Fatalf("unrelated song: %v", err)
	}
	if got := tokenRequests.Load(); got != 1 {
		t.Fatalf("token fetched %d times, want 1", got)
	}
}

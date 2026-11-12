package resolve

import (
	"context"
	"errors"
	"strconv"
	"sync"
	"testing"
	"time"

	"onetune/server/internal/music"
	"onetune/server/internal/music/musictest"
)

var song = music.Entity{Title: "Song", Artist: "Artist", DurationMs: 200000}

func source(name string) *musictest.Fake {
	return &musictest.Fake{PlatformName: name, Entity: song}
}

func target(name string) *musictest.Fake {
	return &musictest.Fake{PlatformName: name, Found: music.Found{URL: "https://" + name + ".example/found"}}
}

func ref(platform, id string) music.Ref { return music.Ref{Platform: platform, Type: "track", ID: id} }

func TestResolveBuildsLinksInProviderOrder(t *testing.T) {
	missing := target("missing")
	missing.FindErr = music.ErrNotFound
	broken := target("broken")
	broken.FindErr = errors.New("upstream down")
	search := target("search")
	search.Found = music.Found{URL: "https://search.example/q", Search: true}

	resolver := New([]music.Provider{search, source("src"), missing, broken, target("other")})
	result, err := resolver.Resolve(context.Background(), ref("src", "1"))
	if err != nil {
		t.Fatal(err)
	}

	var got []string
	for _, link := range result.Links {
		got = append(got, link.Platform+"="+link.URL)
	}
	want := []string{"search=https://search.example/q", "src=https://src.test/track/1", "other=https://other.example/found"}
	if len(got) != len(want) {
		t.Fatalf("links %v, want %v", got, want)
	}
	for index := range want {
		if got[index] != want[index] {
			t.Errorf("link %d: %q, want %q", index, got[index], want[index])
		}
	}
	if !result.Links[0].Search || result.Links[1].Search {
		t.Error("only the search provider's link should be marked as a search")
	}
}

func TestResolveCachesResult(t *testing.T) {
	src := source("src")
	resolver := New([]music.Provider{src, target("other")})
	for range 3 {
		if _, err := resolver.Resolve(context.Background(), ref("src", "1")); err != nil {
			t.Fatal(err)
		}
	}
	if got := src.Lookups.Load(); got != 1 {
		t.Fatalf("source looked up %d times, want 1", got)
	}
}

func TestResolveSharesConcurrentWork(t *testing.T) {
	src := source("src")
	src.Gate = make(chan struct{})
	resolver := New([]music.Provider{src, target("other")})

	var group sync.WaitGroup
	for range 10 {
		group.Add(1)
		go func() {
			defer group.Done()
			if _, err := resolver.Resolve(context.Background(), ref("src", "1")); err != nil {
				t.Error(err)
			}
		}()
	}
	time.Sleep(50 * time.Millisecond)
	close(src.Gate)
	group.Wait()

	if got := src.Lookups.Load(); got != 1 {
		t.Fatalf("source looked up %d times, want 1", got)
	}
}

func TestResolveCachesNotFound(t *testing.T) {
	src := source("src")
	src.LookupErr = music.ErrNotFound
	resolver := New([]music.Provider{src, target("other")})
	for range 3 {
		if _, err := resolver.Resolve(context.Background(), ref("src", "1")); !errors.Is(err, music.ErrNotFound) {
			t.Fatalf("got %v, want ErrNotFound", err)
		}
	}
	if got := src.Lookups.Load(); got != 1 {
		t.Fatalf("source looked up %d times, want 1", got)
	}
}

func TestResolveRejectsWhenBusy(t *testing.T) {
	src := source("src")
	src.Gate = make(chan struct{})
	resolver := New([]music.Provider{src, target("other")})

	var group sync.WaitGroup
	for id := range maxConcurrentResolves {
		group.Add(1)
		go func() {
			defer group.Done()
			_, _ = resolver.Resolve(context.Background(), ref("src", strconv.Itoa(id)))
		}()
	}
	deadline := time.After(5 * time.Second)
	for src.Lookups.Load() < maxConcurrentResolves {
		select {
		case <-deadline:
			t.Fatal("resolutions never filled all slots")
		case <-time.After(5 * time.Millisecond):
		}
	}

	if _, err := resolver.Resolve(context.Background(), ref("src", "overflow")); !errors.Is(err, ErrBusy) {
		t.Fatalf("got %v, want ErrBusy", err)
	}
	close(src.Gate)
	group.Wait()

	// A rejected request must not be remembered as a failure.
	if _, err := resolver.Resolve(context.Background(), ref("src", "overflow")); err != nil {
		t.Fatalf("after slots freed: %v", err)
	}
}

func TestResolveDiscoversISRCThroughDeezer(t *testing.T) {
	deezer := target("deezer")
	deezer.Found = music.Found{URL: "https://deezer.example/found", ISRC: "USQX91300108"}
	tidal := target("tidal")
	resolver := New([]music.Provider{source("src"), deezer, tidal})

	result, err := resolver.Resolve(context.Background(), ref("src", "1"))
	if err != nil {
		t.Fatal(err)
	}
	if result.Entity.ISRC != "USQX91300108" {
		t.Errorf("entity ISRC %q", result.Entity.ISRC)
	}
	if got := tidal.LastFindEntity().ISRC; got != "USQX91300108" {
		t.Errorf("tidal searched with ISRC %q", got)
	}
}

func TestValidRef(t *testing.T) {
	resolver := New([]music.Provider{source("src")})
	cases := []struct {
		ref  music.Ref
		want bool
	}{
		{ref("src", "123"), true},
		{ref("src", ""), false},
		{ref("src", "1/2"), false},
		{ref("src", "1?x=2"), false},
		{ref("src", "a b"), false},
		{ref("other", "123"), false},
		{music.Ref{Platform: "src", Type: "album", ID: "123"}, false},
	}
	for _, test := range cases {
		if got := resolver.ValidRef(test.ref); got != test.want {
			t.Errorf("%+v: got %v, want %v", test.ref, got, test.want)
		}
	}
}

func TestResolveDoesNotCacheIncompleteResultForLong(t *testing.T) {
	src := source("src")
	broken := target("broken")
	broken.FindErr = errors.New("upstream down")
	resolver := New([]music.Provider{src, broken})
	if _, err := resolver.Resolve(context.Background(), ref("src", "1")); err != nil {
		t.Fatal(err)
	}
	entry, ok := resolver.fromCache(ref("src", "1"))
	if !ok {
		t.Fatal("result not cached")
	}
	if remaining := time.Until(entry.expires); remaining > failureTTL {
		t.Fatalf("incomplete result cached for %v, want at most %v", remaining, failureTTL)
	}
}

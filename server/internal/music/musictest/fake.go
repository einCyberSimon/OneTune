// Package musictest provides a controllable music.Provider for tests.
package musictest

import (
	"context"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"sync/atomic"

	"onetune/server/internal/music"
)

type Fake struct {
	PlatformName string
	Entity       music.Entity
	LookupErr    error
	Found        music.Found
	FindErr      error
	// Gate, when set, blocks Lookup until it is closed.
	Gate chan struct{}

	Lookups atomic.Int32
	Finds   atomic.Int32

	mutex          sync.Mutex
	lastFindEntity music.Entity
}

func (f *Fake) Name() string  { return f.PlatformName }
func (f *Fake) Label() string { return strings.ToUpper(f.PlatformName) }

func (f *Fake) Parse(link *url.URL) (string, bool) {
	id, found := strings.CutPrefix(link.Path, "/track/")
	if link.Hostname() != f.PlatformName+".test" || !found || !validID.MatchString(id) {
		return "", false
	}
	return id, true
}

var validID = regexp.MustCompile(`^[A-Za-z0-9_-]+$`)

func (f *Fake) WebURL(id string) string { return "https://" + f.PlatformName + ".test/track/" + id }

func (f *Fake) Lookup(ctx context.Context, _ string) (music.Entity, error) {
	f.Lookups.Add(1)
	if f.Gate != nil {
		select {
		case <-f.Gate:
		case <-ctx.Done():
			return music.Entity{}, ctx.Err()
		}
	}
	return f.Entity, f.LookupErr
}

func (f *Fake) Find(_ context.Context, entity music.Entity) (music.Found, error) {
	f.Finds.Add(1)
	f.mutex.Lock()
	f.lastFindEntity = entity
	f.mutex.Unlock()
	return f.Found, f.FindErr
}

// LastFindEntity returns the entity most recently passed to Find.
func (f *Fake) LastFindEntity() music.Entity {
	f.mutex.Lock()
	defer f.mutex.Unlock()
	return f.lastFindEntity
}

package resolve

import (
	"context"
	"errors"
	"log"
	"sync"
	"time"

	"golang.org/x/sync/singleflight"

	"onetune/server/internal/music"
)

const (
	cacheTTL              = 24 * time.Hour
	notFoundTTL           = 10 * time.Minute
	failureTTL            = 30 * time.Second
	maxCacheItems         = 10000
	maxConcurrentResolves = 32
	resolveBudget         = 20 * time.Second
)

// ErrBusy is returned instead of queueing when too many resolutions are in flight.
var ErrBusy = errors.New("too many concurrent resolutions")

type Link struct {
	Platform string `json:"platform"`
	Label    string `json:"label"`
	URL      string `json:"url"`
	Search   bool   `json:"search,omitempty"`
}

type Result struct {
	Source music.Ref    `json:"source"`
	Entity music.Entity `json:"entity"`
	Links  []Link       `json:"links"`
}

type cached struct {
	result  Result
	err     error
	expires time.Time
}

// Resolver maps a source track to links on every other platform. Results are
// cached per track (never per user) and concurrent requests for the same
// track share one upstream resolution.
type Resolver struct {
	providers []music.Provider
	flight    singleflight.Group
	slots     chan struct{}

	mutex sync.Mutex
	cache map[music.Ref]cached // shortcut: lost on restart and bounded by a crude clear, move to SQLite when restarts hurt
}

func New(providers []music.Provider) *Resolver {
	return &Resolver{
		providers: providers,
		slots:     make(chan struct{}, maxConcurrentResolves),
		cache:     map[music.Ref]cached{},
	}
}

func (r *Resolver) Providers() []music.Provider { return r.providers }

func (r *Resolver) provider(name string) music.Provider {
	for _, provider := range r.providers {
		if provider.Name() == name {
			return provider
		}
	}
	return nil
}

// ValidRef reports whether ref names a supported platform and a well-formed ID.
func (r *Resolver) ValidRef(ref music.Ref) bool {
	provider := r.provider(ref.Platform)
	if provider == nil || ref.Type != "track" {
		return false
	}
	parsed, ok := music.ParseURL(r.providers, provider.WebURL(ref.ID))
	return ok && parsed == ref
}

func (r *Resolver) Resolve(ctx context.Context, ref music.Ref) (Result, error) {
	if entry, ok := r.fromCache(ref); ok {
		return entry.result, entry.err
	}
	// The shared flight must outlive the first caller's request.
	value, err, _ := r.flight.Do(ref.Platform+"/"+ref.ID, func() (any, error) {
		select {
		case r.slots <- struct{}{}:
			defer func() { <-r.slots }()
		default:
			return nil, ErrBusy
		}
		budget, cancel := context.WithTimeout(context.WithoutCancel(ctx), resolveBudget)
		defer cancel()
		result, err := r.resolve(budget, ref)
		r.store(ref, result, err)
		return result, err
	})
	if err != nil {
		return Result{}, err
	}
	return value.(Result), nil
}

func (r *Resolver) resolve(ctx context.Context, ref music.Ref) (Result, error) {
	source := r.provider(ref.Platform)
	if source == nil {
		return Result{}, music.ErrNotFound
	}
	entity, err := source.Lookup(ctx, ref.ID)
	if err != nil {
		return Result{}, err
	}
	if entity.Title == "" {
		return Result{}, errors.New("source returned no title")
	}
	if entity.ISRC == "" {
		entity.ISRC = r.discoverISRC(ctx, entity)
	}

	found := make([]*music.Found, len(r.providers))
	var group sync.WaitGroup
	for index, provider := range r.providers {
		if provider.Name() == ref.Platform {
			continue
		}
		group.Add(1)
		go func() {
			defer group.Done()
			match, err := provider.Find(ctx, entity)
			if err != nil {
				if !errors.Is(err, music.ErrNotFound) {
					log.Printf("find on %s failed: %v", provider.Name(), err)
				}
				return
			}
			found[index] = &match
		}()
	}
	group.Wait()

	result := Result{Source: ref, Entity: entity}
	for index, provider := range r.providers {
		switch {
		case provider.Name() == ref.Platform:
			result.Links = append(result.Links, Link{Platform: ref.Platform, Label: provider.Label(), URL: provider.WebURL(ref.ID)})
		case found[index] != nil:
			match := found[index]
			result.Links = append(result.Links, Link{Platform: provider.Name(), Label: provider.Label(), URL: match.URL, Search: match.Search})
		}
	}
	return result, nil
}

// discoverISRC asks the platforms that expose ISRCs (Deezer) to identify a
// source that does not, so ISRC-only targets such as Tidal still work.
func (r *Resolver) discoverISRC(ctx context.Context, entity music.Entity) string {
	deezer := r.provider("deezer")
	if deezer == nil {
		return ""
	}
	match, err := deezer.Find(ctx, entity)
	if err != nil {
		return ""
	}
	return match.ISRC
}

func (r *Resolver) fromCache(ref music.Ref) (cached, bool) {
	r.mutex.Lock()
	defer r.mutex.Unlock()
	entry, ok := r.cache[ref]
	if !ok || time.Now().After(entry.expires) {
		return cached{}, false
	}
	return entry, true
}

// store also caches failures, so repeated requests for unknown IDs cannot
// drain the upstream rate limits.
func (r *Resolver) store(ref music.Ref, result Result, err error) {
	ttl := cacheTTL
	switch {
	case errors.Is(err, music.ErrNotFound):
		ttl = notFoundTTL
	case err != nil:
		ttl = failureTTL
	}
	r.mutex.Lock()
	defer r.mutex.Unlock()
	if len(r.cache) >= maxCacheItems {
		clear(r.cache)
	}
	r.cache[ref] = cached{result: result, err: err, expires: time.Now().Add(ttl)}
}

package music

import (
	"context"
	"errors"
	"net/url"
	"regexp"
	"strings"
)

var ErrNotFound = errors.New("not found")

type Ref struct {
	Platform string `json:"platform"`
	Type     string `json:"type"`
	ID       string `json:"id"`
}

type Entity struct {
	Title      string `json:"title"`
	Artist     string `json:"artist"`
	DurationMs int    `json:"durationMs,omitempty"`
	ISRC       string `json:"isrc,omitempty"`
	ArtworkURL string `json:"artworkUrl,omitempty"`
}

// Found is the result of locating an entity on another platform.
// Search is true when URL is only a search page, not the exact track.
type Found struct {
	URL    string
	ISRC   string
	Search bool
}

type Provider interface {
	Name() string
	Label() string
	Parse(link *url.URL) (id string, ok bool)
	WebURL(id string) string
	Lookup(ctx context.Context, id string) (Entity, error)
	Find(ctx context.Context, entity Entity) (Found, error)
}

func ParseURL(providers []Provider, raw string) (Ref, bool) {
	link, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || (link.Scheme != "https" && link.Scheme != "http") {
		return Ref{}, false
	}
	for _, provider := range providers {
		if id, ok := provider.Parse(link); ok {
			return Ref{Platform: provider.Name(), Type: "track", ID: id}, true
		}
	}
	return Ref{}, false
}

func hostIn(link *url.URL, hosts ...string) bool {
	host := strings.ToLower(link.Hostname())
	for _, candidate := range hosts {
		if host == candidate {
			return true
		}
	}
	return false
}

// matchPath returns the first capture group of pattern against the URL path.
func matchPath(link *url.URL, pattern *regexp.Regexp) (string, bool) {
	groups := pattern.FindStringSubmatch(link.Path)
	if groups == nil {
		return "", false
	}
	return groups[1], true
}

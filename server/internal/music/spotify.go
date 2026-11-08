package music

import (
	"context"
	"html"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

var (
	spotifyPath        = regexp.MustCompile(`^/(?:intl-[a-z]{2}(?:-[a-zA-Z]+)?/)?(?:embed/)?track/([0-9A-Za-z]{22})/?$`)
	spotifyMetaPattern = regexp.MustCompile(`<meta (?:property|name)="(?:og|music):(title|description|image|duration)" content="([^"]*)"`)
)

// Spotify has no free credentials for us (developer mode requires Premium),
// so tracks are read from the public page metadata and the target link is a
// search page.
type Spotify struct {
	upstream *upstream
	pageBase string
}

func NewSpotify() *Spotify {
	return &Spotify{upstream: newUpstream(2, 4), pageBase: "https://open.spotify.com"}
}

func (*Spotify) Name() string  { return "spotify" }
func (*Spotify) Label() string { return "Spotify" }

func (*Spotify) Parse(link *url.URL) (string, bool) {
	if !hostIn(link, "open.spotify.com") {
		return "", false
	}
	return matchPath(link, spotifyPath)
}

func (*Spotify) WebURL(id string) string { return "https://open.spotify.com/track/" + id }

func (s *Spotify) Lookup(ctx context.Context, id string) (Entity, error) {
	data, err := s.upstream.do(ctx, http.MethodGet, s.pageBase+"/track/"+id, nil, http.Header{"User-Agent": {"Mozilla/5.0"}})
	if err != nil {
		return Entity{}, err
	}
	var entity Entity
	for _, group := range spotifyMetaPattern.FindAllStringSubmatch(string(data), -1) {
		value := html.UnescapeString(group[2])
		switch group[1] {
		case "title":
			entity.Title = value
		case "description":
			// "Artist, Artist · Album · Song · 2013"
			entity.Artist, _, _ = strings.Cut(value, " · ")
		case "image":
			entity.ArtworkURL = value
		case "duration":
			seconds, _ := strconv.Atoi(value)
			entity.DurationMs = seconds * 1000
		}
	}
	if entity.Title == "" {
		return Entity{}, ErrNotFound
	}
	return entity, nil
}

func (*Spotify) Find(_ context.Context, entity Entity) (Found, error) {
	query := url.PathEscape(entity.Artist + " " + entity.Title)
	return Found{URL: "https://open.spotify.com/search/" + query, Search: true}, nil
}

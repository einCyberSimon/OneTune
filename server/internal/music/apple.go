package music

import (
	"context"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

var (
	appleSongPath     = regexp.MustCompile(`^/[a-z]{2}/song/(?:[^/]+/)?([0-9]+)/?$`)
	appleTrackIDQuery = regexp.MustCompile(`^[0-9]+$`)
	appleAlbumPath    = regexp.MustCompile(`^/[a-z]{2}/album/[^/]+/[0-9]+/?$`)
)

// Apple uses the free iTunes Search API (documented limit: ~20 calls/minute).
// It exposes no ISRC, so matching relies on title, artist and duration.
type Apple struct {
	upstream *upstream
	apiBase  string
}

func NewApple() *Apple {
	return &Apple{upstream: newUpstream(0.3, 3), apiBase: "https://itunes.apple.com"}
}

func (*Apple) Name() string  { return "apple" }
func (*Apple) Label() string { return "Apple Music" }

func (*Apple) Parse(link *url.URL) (string, bool) {
	if !hostIn(link, "music.apple.com", "itunes.apple.com") {
		return "", false
	}
	if id, ok := matchPath(link, appleSongPath); ok {
		return id, true
	}
	trackID := link.Query().Get("i")
	if appleAlbumPath.MatchString(link.Path) && appleTrackIDQuery.MatchString(trackID) {
		return trackID, true
	}
	return "", false
}

func (*Apple) WebURL(id string) string { return "https://music.apple.com/us/song/" + id }

type itunesResponse struct {
	Results []struct {
		Kind         string `json:"kind"`
		TrackID      int64  `json:"trackId"`
		TrackName    string `json:"trackName"`
		ArtistName   string `json:"artistName"`
		DurationMs   int    `json:"trackTimeMillis"`
		ArtworkURL   string `json:"artworkUrl100"`
		TrackViewURL string `json:"trackViewUrl"`
	} `json:"results"`
}

func (a *Apple) Lookup(ctx context.Context, id string) (Entity, error) {
	var response itunesResponse
	if err := a.upstream.getJSON(ctx, a.apiBase+"/lookup?entity=song&id="+url.QueryEscape(id), nil, &response); err != nil {
		return Entity{}, err
	}
	for _, result := range response.Results {
		if result.Kind == "song" {
			return Entity{
				Title:      result.TrackName,
				Artist:     result.ArtistName,
				DurationMs: result.DurationMs,
				ArtworkURL: strings.Replace(result.ArtworkURL, "100x100", "600x600", 1),
			}, nil
		}
	}
	return Entity{}, ErrNotFound
}

func (a *Apple) Find(ctx context.Context, entity Entity) (Found, error) {
	var response itunesResponse
	query := url.Values{"term": {entity.Artist + " " + entity.Title}, "entity": {"song"}, "media": {"music"}, "limit": {"10"}}
	if err := a.upstream.getJSON(ctx, a.apiBase+"/search?"+query.Encode(), nil, &response); err != nil {
		return Found{}, err
	}
	for _, result := range response.Results {
		candidate := Entity{Title: result.TrackName, Artist: result.ArtistName, DurationMs: result.DurationMs}
		if result.Kind == "song" && SameSong(entity, candidate) {
			return Found{URL: a.WebURL(strconv.FormatInt(result.TrackID, 10))}, nil
		}
	}
	return Found{}, ErrNotFound
}

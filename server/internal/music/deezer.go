package music

import (
	"context"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

var (
	deezerPath  = regexp.MustCompile(`^/(?:[a-z]{2}/)?track/([0-9]+)/?$`)
	isrcPattern = regexp.MustCompile(`^[A-Z0-9]{12}$`)
)

type Deezer struct {
	upstream *upstream
	apiBase  string
}

func NewDeezer() *Deezer {
	return &Deezer{upstream: newUpstream(5, 5), apiBase: "https://api.deezer.com"}
}

func (*Deezer) Name() string  { return "deezer" }
func (*Deezer) Label() string { return "Deezer" }

func (*Deezer) Parse(link *url.URL) (string, bool) {
	if !hostIn(link, "www.deezer.com", "deezer.com") {
		return "", false
	}
	return matchPath(link, deezerPath)
}

func (*Deezer) WebURL(id string) string { return "https://www.deezer.com/track/" + id }

type deezerTrack struct {
	ID       int64  `json:"id"`
	Title    string `json:"title"`
	ISRC     string `json:"isrc"`
	Duration int    `json:"duration"`
	Artist   struct {
		Name string `json:"name"`
	} `json:"artist"`
	Album struct {
		Cover string `json:"cover_big"`
	} `json:"album"`
	Error *struct{} `json:"error"`
}

func (track deezerTrack) entity() Entity {
	return Entity{
		Title:      track.Title,
		Artist:     track.Artist.Name,
		DurationMs: track.Duration * 1000,
		ISRC:       track.ISRC,
		ArtworkURL: track.Album.Cover,
	}
}

func (d *Deezer) fetchTrack(ctx context.Context, path string) (deezerTrack, error) {
	var track deezerTrack
	if err := d.upstream.getJSON(ctx, d.apiBase+"/track/"+path, nil, &track); err != nil {
		return track, err
	}
	// Deezer answers 200 with an error object for unknown tracks.
	if track.Error != nil || track.ID == 0 {
		return track, ErrNotFound
	}
	return track, nil
}

func (d *Deezer) Lookup(ctx context.Context, id string) (Entity, error) {
	track, err := d.fetchTrack(ctx, id)
	return track.entity(), err
}

func (d *Deezer) Find(ctx context.Context, entity Entity) (Found, error) {
	if isrcPattern.MatchString(entity.ISRC) {
		if track, err := d.fetchTrack(ctx, "isrc:"+entity.ISRC); err == nil {
			return Found{URL: d.WebURL(strconv.FormatInt(track.ID, 10)), ISRC: track.ISRC}, nil
		}
	}
	var results struct {
		Data []deezerTrack `json:"data"`
	}
	query := url.QueryEscape(entity.Artist + " " + entity.Title)
	if err := d.upstream.getJSON(ctx, d.apiBase+"/search/track?limit=10&q="+query, nil, &results); err != nil {
		return Found{}, err
	}
	for _, candidate := range results.Data {
		if !SameSong(entity, candidate.entity()) {
			continue
		}
		id := strconv.FormatInt(candidate.ID, 10)
		// Search results omit the ISRC.
		full, err := d.fetchTrack(ctx, id)
		if err != nil {
			return Found{URL: d.WebURL(id)}, nil
		}
		return Found{URL: d.WebURL(id), ISRC: strings.TrimSpace(full.ISRC)}, nil
	}
	return Found{}, ErrNotFound
}

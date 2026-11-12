package music

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"slices"
	"strings"
	"sync"
	"time"
)

const maxSearchQueryRunes = 200

var tidalPath = regexp.MustCompile(`^/(?:browse/)?(?:album/[0-9]+/)?track/([0-9]+)(?:/u)?/?$`)

// Tidal uses the official OpenAPI v2 with client credentials. Responses are
// JSON:API documents: tracks reference artists by ID and the artists arrive in
// "included". The response shapes are recorded in testdata/tidal_*.json.
type Tidal struct {
	upstream     *upstream
	authURL      string
	apiBase      string
	clientID     string
	clientSecret string
	countryCode  string

	mutex       sync.Mutex
	token       string
	tokenExpiry time.Time
}

// NewTidal creates the provider. countryCode (ISO 3166-1 alpha-2) selects the
// catalog whose availability Tidal reports.
func NewTidal(clientID, clientSecret, countryCode string) *Tidal {
	return &Tidal{
		upstream:     newUpstream(3, 5),
		authURL:      "https://auth.tidal.com/v1/oauth2/token",
		apiBase:      "https://openapi.tidal.com/v2",
		clientID:     clientID,
		clientSecret: clientSecret,
		countryCode:  countryCode,
	}
}

func (*Tidal) Name() string  { return "tidal" }
func (*Tidal) Label() string { return "Tidal" }

func (*Tidal) Parse(link *url.URL) (string, bool) {
	if !hostIn(link, "tidal.com", "listen.tidal.com", "www.tidal.com") {
		return "", false
	}
	return matchPath(link, tidalPath)
}

func (*Tidal) WebURL(id string) string { return "https://tidal.com/track/" + id + "/u" }

func (t *Tidal) authHeader(ctx context.Context) (http.Header, error) {
	t.mutex.Lock()
	defer t.mutex.Unlock()
	if t.token == "" || time.Now().After(t.tokenExpiry) {
		credentials := base64.StdEncoding.EncodeToString([]byte(t.clientID + ":" + t.clientSecret))
		header := http.Header{
			"Authorization": {"Basic " + credentials},
			"Content-Type":  {"application/x-www-form-urlencoded"},
		}
		data, err := t.upstream.do(ctx, http.MethodPost, t.authURL, []byte("grant_type=client_credentials"), header)
		if err != nil {
			return nil, err
		}
		var token struct {
			AccessToken string `json:"access_token"`
			ExpiresIn   int    `json:"expires_in"`
		}
		if err := json.Unmarshal(data, &token); err != nil {
			return nil, fmt.Errorf("tidal token response: %w", err)
		}
		if token.AccessToken == "" {
			return nil, errors.New("tidal token response has no access_token")
		}
		t.token = token.AccessToken
		t.tokenExpiry = time.Now().Add(time.Duration(token.ExpiresIn-60) * time.Second)
	}
	return http.Header{"Authorization": {"Bearer " + t.token}, "Accept": {"application/vnd.api+json"}}, nil
}

type tidalReference struct {
	ID string `json:"id"`
}

type tidalResource struct {
	ID         string `json:"id"`
	Type       string `json:"type"`
	Attributes struct {
		Name         string   `json:"name"`
		Title        string   `json:"title"`
		ISRC         string   `json:"isrc"`
		Duration     string   `json:"duration"`
		Availability []string `json:"availability"`
	} `json:"attributes"`
	Relationships struct {
		Artists struct {
			Data []tidalReference `json:"data"`
		} `json:"artists"`
		Tracks struct {
			Data []tidalReference `json:"data"`
		} `json:"tracks"`
	} `json:"relationships"`
}

type tidalIncluded []tidalResource

func (included tidalIncluded) get(resourceType, id string) (tidalResource, bool) {
	for _, resource := range included {
		if resource.Type == resourceType && resource.ID == id {
			return resource, true
		}
	}
	return tidalResource{}, false
}

// entity builds an Entity with artists in the track's own order; "included" is unordered.
func (included tidalIncluded) entity(track tidalResource) Entity {
	var names []string
	for _, reference := range track.Relationships.Artists.Data {
		if artist, ok := included.get("artists", reference.ID); ok {
			names = append(names, artist.Attributes.Name)
		}
	}
	return Entity{
		Title:      track.Attributes.Title,
		Artist:     strings.Join(names, ", "),
		ISRC:       track.Attributes.ISRC,
		DurationMs: parseISODuration(track.Attributes.Duration),
	}
}

func (t *Tidal) Lookup(ctx context.Context, id string) (Entity, error) {
	header, err := t.authHeader(ctx)
	if err != nil {
		return Entity{}, err
	}
	var response struct {
		Data     tidalResource `json:"data"`
		Included tidalIncluded `json:"included"`
	}
	target := t.apiBase + "/tracks/" + url.PathEscape(id) + "?include=artists&countryCode=" + t.countryCode
	if err := t.upstream.getJSON(ctx, target, header, &response); err != nil {
		return Entity{}, err
	}
	return response.Included.entity(response.Data), nil
}

// Find looks the track up by ISRC and, when that finds nothing, by title and artist.
func (t *Tidal) Find(ctx context.Context, entity Entity) (Found, error) {
	header, err := t.authHeader(ctx)
	if err != nil {
		return Found{}, err
	}
	if isrcPattern.MatchString(entity.ISRC) {
		if found, err := t.findByISRC(ctx, header, entity.ISRC); err == nil {
			return found, nil
		}
	}
	return t.findBySearch(ctx, header, entity)
}

func (t *Tidal) findByISRC(ctx context.Context, header http.Header, isrc string) (Found, error) {
	var response struct {
		Data []tidalResource `json:"data"`
	}
	target := t.apiBase + "/tracks?countryCode=" + t.countryCode + "&filter%5Bisrc%5D=" + url.QueryEscape(isrc)
	if err := t.upstream.getJSON(ctx, target, header, &response); err != nil {
		return Found{}, err
	}
	if len(response.Data) == 0 {
		return Found{}, ErrNotFound
	}
	// One recording has many Tidal releases. Prefer one marked streamable:
	// the unmarked first release of "Get Lucky" was unavailable in Germany.
	best := response.Data[0]
	for _, track := range response.Data {
		if slices.Contains(track.Attributes.Availability, "STREAM") {
			best = track
			break
		}
	}
	return Found{URL: t.WebURL(best.ID), ISRC: isrc}, nil
}

func (t *Tidal) findBySearch(ctx context.Context, header http.Header, entity Entity) (Found, error) {
	query := []rune(entity.Artist + " " + entity.Title)
	if len(query) > maxSearchQueryRunes {
		query = query[:maxSearchQueryRunes]
	}
	var response struct {
		Data     []tidalResource `json:"data"`
		Included tidalIncluded   `json:"included"`
	}
	target := t.apiBase + "/searchResults?countryCode=" + t.countryCode +
		"&include=tracks,tracks.artists&filter%5Bquery%5D=" + url.QueryEscape(string(query))
	if err := t.upstream.getJSON(ctx, target, header, &response); err != nil {
		return Found{}, err
	}
	if len(response.Data) == 0 {
		return Found{}, ErrNotFound
	}
	for _, reference := range response.Data[0].Relationships.Tracks.Data {
		track, ok := response.Included.get("tracks", reference.ID)
		if ok && SameSong(entity, response.Included.entity(track)) {
			return Found{URL: t.WebURL(track.ID), ISRC: track.Attributes.ISRC}, nil
		}
	}
	return Found{}, ErrNotFound
}

// parseISODuration handles the "PT6M7S" form.
func parseISODuration(duration string) int {
	duration = strings.TrimPrefix(duration, "PT")
	total := 0.0
	number := ""
	for _, character := range duration {
		switch character {
		case 'H', 'M', 'S':
			value, err := time.ParseDuration(number + strings.ToLower(string(character)))
			if err != nil {
				return 0
			}
			total += value.Seconds()
			number = ""
		default:
			number += string(character)
		}
	}
	return int(total * 1000)
}

package music

import (
	"context"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

var youtubeVideoID = regexp.MustCompile(`^[A-Za-z0-9_-]{11}$`)

// YouTubeMusic uses YouTube's unofficial InnerTube endpoints (no key, no
// quota, no stability guarantee). Response shapes are walked defensively.
type YouTubeMusic struct {
	upstream *upstream
	apiBase  string
}

func NewYouTubeMusic() *YouTubeMusic {
	return &YouTubeMusic{upstream: newUpstream(2, 4), apiBase: "https://music.youtube.com"}
}

func (*YouTubeMusic) Name() string  { return "ytmusic" }
func (*YouTubeMusic) Label() string { return "YouTube Music" }

func (*YouTubeMusic) Parse(link *url.URL) (string, bool) {
	var id string
	switch {
	case hostIn(link, "music.youtube.com", "www.youtube.com", "youtube.com", "m.youtube.com"):
		if link.Path != "/watch" {
			return "", false
		}
		id = link.Query().Get("v")
	case hostIn(link, "youtu.be"):
		id = strings.TrimPrefix(link.Path, "/")
	}
	return id, youtubeVideoID.MatchString(id)
}

func (*YouTubeMusic) WebURL(id string) string { return "https://music.youtube.com/watch?v=" + id }

func innertubeContext() map[string]any {
	return map[string]any{"client": map[string]any{"clientName": "WEB_REMIX", "clientVersion": "1.20250101.01.00", "hl": "en"}}
}

func (y *YouTubeMusic) Lookup(ctx context.Context, id string) (Entity, error) {
	var response struct {
		VideoDetails struct {
			Title         string `json:"title"`
			Author        string `json:"author"`
			LengthSeconds string `json:"lengthSeconds"`
			Thumbnail     struct {
				Thumbnails []struct {
					URL string `json:"url"`
				} `json:"thumbnails"`
			} `json:"thumbnail"`
		} `json:"videoDetails"`
	}
	payload := map[string]any{"context": innertubeContext(), "videoId": id}
	if err := y.upstream.postJSON(ctx, y.apiBase+"/youtubei/v1/player?prettyPrint=false", payload, &response); err != nil {
		return Entity{}, err
	}
	details := response.VideoDetails
	if details.Title == "" {
		return Entity{}, ErrNotFound
	}
	seconds, _ := strconv.Atoi(details.LengthSeconds)
	entity := Entity{Title: details.Title, Artist: strings.ReplaceAll(details.Author, ", &", " &"), DurationMs: seconds * 1000}
	if thumbnails := details.Thumbnail.Thumbnails; len(thumbnails) > 0 {
		entity.ArtworkURL = thumbnails[len(thumbnails)-1].URL
	}
	return entity, nil
}

func (y *YouTubeMusic) Find(ctx context.Context, entity Entity) (Found, error) {
	var response any
	payload := map[string]any{
		"context": innertubeContext(),
		"query":   entity.Artist + " " + entity.Title,
		// Restricts results to the "Songs" shelf.
		"params": "EgWKAQIIAWoQEAMQBBAJEAoQBRAQEBUQEQ==",
	}
	if err := y.upstream.postJSON(ctx, y.apiBase+"/youtubei/v1/search?prettyPrint=false", payload, &response); err != nil {
		return Found{}, err
	}
	for _, item := range findObjects(response, "musicResponsiveListItemRenderer") {
		videoID, candidate := parseSearchItem(item)
		if youtubeVideoID.MatchString(videoID) && SameSong(entity, candidate) {
			return Found{URL: y.WebURL(videoID)}, nil
		}
	}
	return Found{}, ErrNotFound
}

// parseSearchItem reads a song row: column 0 is the title, column 1 is
// "Artist, Artist • Album • m:ss".
func parseSearchItem(item map[string]any) (string, Entity) {
	columns, _ := item["flexColumns"].([]any)
	if len(columns) < 2 {
		return "", Entity{}
	}
	title := flexColumnText(columns[0])
	parts := strings.Split(flexColumnText(columns[1]), " • ")
	entity := Entity{Title: title, Artist: parts[0]}
	if len(parts) > 1 {
		entity.DurationMs = parseClock(parts[len(parts)-1])
	}
	videoID := ""
	if data, ok := item["playlistItemData"].(map[string]any); ok {
		videoID, _ = data["videoId"].(string)
	}
	return videoID, entity
}

func flexColumnText(column any) string {
	var builder strings.Builder
	columnMap, _ := column.(map[string]any)
	renderer, _ := columnMap["musicResponsiveListItemFlexColumnRenderer"].(map[string]any)
	text, _ := renderer["text"].(map[string]any)
	runs, _ := text["runs"].([]any)
	for _, run := range runs {
		if runMap, ok := run.(map[string]any); ok {
			value, _ := runMap["text"].(string)
			builder.WriteString(value)
		}
	}
	return builder.String()
}

func parseClock(clock string) int {
	minutes, seconds, found := strings.Cut(strings.TrimSpace(clock), ":")
	if !found {
		return 0
	}
	minuteCount, minuteErr := strconv.Atoi(minutes)
	secondCount, secondErr := strconv.Atoi(seconds)
	if minuteErr != nil || secondErr != nil {
		return 0
	}
	return (minuteCount*60 + secondCount) * 1000
}

// findObjects collects every value stored under key anywhere in a decoded JSON tree.
func findObjects(node any, key string) []map[string]any {
	var found []map[string]any
	switch value := node.(type) {
	case map[string]any:
		for name, child := range value {
			if name == key {
				if object, ok := child.(map[string]any); ok {
					found = append(found, object)
				}
				continue
			}
			found = append(found, findObjects(child, key)...)
		}
	case []any:
		for _, child := range value {
			found = append(found, findObjects(child, key)...)
		}
	}
	return found
}

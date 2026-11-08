package music

import (
	"encoding/json"
	"os"
	"testing"
)

func testProviders() []Provider {
	return []Provider{NewSpotify(), NewApple(), NewTidal("", "", "US"), NewDeezer(), NewYouTubeMusic()}
}

func TestParseURLVectors(t *testing.T) {
	data, err := os.ReadFile("../../../testvectors/links.json")
	if err != nil {
		t.Fatal(err)
	}
	var vectors struct {
		Valid   []struct{ URL, Platform, ID string }
		Invalid []string
	}
	if err := json.Unmarshal(data, &vectors); err != nil {
		t.Fatal(err)
	}
	providers := testProviders()
	for _, vector := range vectors.Valid {
		ref, ok := ParseURL(providers, vector.URL)
		if !ok || ref.Platform != vector.Platform || ref.ID != vector.ID || ref.Type != "track" {
			t.Errorf("%s: got %+v ok=%v, want %s/%s", vector.URL, ref, ok, vector.Platform, vector.ID)
		}
	}
	for _, raw := range vectors.Invalid {
		if ref, ok := ParseURL(providers, raw); ok {
			t.Errorf("%q should be rejected, got %+v", raw, ref)
		}
	}
}

func TestSameSong(t *testing.T) {
	base := Entity{Title: "Get Lucky (feat. Pharrell Williams and Nile Rodgers)", Artist: "Daft Punk, Pharrell Williams & Nile Rodgers", DurationMs: 369000}
	cases := []struct {
		name  string
		other Entity
		want  bool
	}{
		{"same", base, true},
		{"qualifier differs", Entity{Title: "Get Lucky - Remastered", Artist: "Daft Punk", DurationMs: 367000}, true},
		{"radio edit duration", Entity{Title: "Get Lucky", Artist: "Daft Punk", DurationMs: 249000}, false},
		{"cover artist", Entity{Title: "Get Lucky", Artist: "Funk Punk", DurationMs: 369000}, false},
		{"other title", Entity{Title: "Lose Yourself", Artist: "Daft Punk", DurationMs: 369000}, false},
		{"isrc wins", Entity{Title: "x", Artist: "y", ISRC: "A"}, false},
	}
	for _, test := range cases {
		if got := SameSong(base, test.other); got != test.want {
			t.Errorf("%s: got %v want %v", test.name, got, test.want)
		}
	}
	if !SameSong(Entity{ISRC: "A"}, Entity{Title: "x", ISRC: "A"}) {
		t.Error("equal ISRC should match")
	}
}

func TestParseISODuration(t *testing.T) {
	if got := parseISODuration("PT6M7S"); got != 367000 {
		t.Errorf("got %d", got)
	}
}

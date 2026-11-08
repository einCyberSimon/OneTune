package music

import (
	"strings"
	"unicode"
)

const maxDurationDifferenceMs = 5000

// SameSong reports whether two entities plausibly describe the same recording.
func SameSong(a, b Entity) bool {
	if a.ISRC != "" && a.ISRC == b.ISRC {
		return true
	}
	if normalizeTitle(a.Title) != normalizeTitle(b.Title) {
		return false
	}
	if !sameLeadArtist(a.Artist, b.Artist) {
		return false
	}
	if a.DurationMs > 0 && b.DurationMs > 0 {
		difference := a.DurationMs - b.DurationMs
		if difference < 0 {
			difference = -difference
		}
		return difference <= maxDurationDifferenceMs
	}
	return true
}

// normalizeTitle drops bracketed and dash-suffixed qualifiers such as
// "(feat. X)" or "- Remastered", which differ between platforms.
func normalizeTitle(title string) string {
	title = strings.ToLower(title)
	if before, _, found := strings.Cut(title, " - "); found {
		title = before
	}
	var builder strings.Builder
	depth := 0
	for _, character := range title {
		switch {
		case character == '(' || character == '[':
			depth++
		case character == ')' || character == ']':
			if depth > 0 {
				depth--
			}
		case depth == 0 && (unicode.IsLetter(character) || unicode.IsDigit(character)):
			builder.WriteRune(character)
		}
	}
	return builder.String()
}

func sameLeadArtist(a, b string) bool {
	leadA, leadB := normalizeLetters(leadArtist(a)), normalizeLetters(leadArtist(b))
	if leadA == "" || leadB == "" {
		return false
	}
	return strings.Contains(normalizeLetters(b), leadA) || strings.Contains(normalizeLetters(a), leadB)
}

func leadArtist(artists string) string {
	for _, separator := range []string{",", "&", " feat", " ft.", " x ", " and "} {
		if before, _, found := strings.Cut(strings.ToLower(artists), separator); found {
			artists = before
		}
	}
	return artists
}

func normalizeLetters(text string) string {
	var builder strings.Builder
	for _, character := range strings.ToLower(text) {
		if unicode.IsLetter(character) || unicode.IsDigit(character) {
			builder.WriteRune(character)
		}
	}
	return builder.String()
}

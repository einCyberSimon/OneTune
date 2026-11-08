#!/usr/bin/env bash
# Re-records provider fixtures from the live APIs. Run manually when a provider's
# response format changes; the regular tests only read the committed files.
# Tidal fixtures are hand-written from the API documentation (no live credentials
# yet) and are not touched here.
set -euo pipefail

out="$(dirname "$0")/../internal/music/testdata"
mkdir -p "$out"
user_agent="Mozilla/5.0"
innertube_context='{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20250101.01.00","hl":"en"}}'

curl -fsS "https://api.deezer.com/track/67238735" | jq . > "$out/deezer_track.json"
curl -fsS "https://api.deezer.com/search/track?limit=10&q=Daft+Punk+Get+Lucky" | jq '{data: .data[:5]}' > "$out/deezer_search.json"

curl -fsS "https://itunes.apple.com/lookup?entity=song&id=617154366" | jq . > "$out/apple_lookup.json"
curl -fsS "https://itunes.apple.com/search?term=daft+punk+get+lucky&entity=song&media=music&limit=10" | jq . > "$out/apple_search.json"

{
  echo "<html><head>"
  curl -fsS -A "$user_agent" "https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq" |
    grep -oE '<meta (property|name)="(og:(title|description|image)|music:(duration|musician_description))" [^>]*>' | sort -u
  echo "</head></html>"
} > "$out/spotify_track.html"

curl -fsS -X POST "https://music.youtube.com/youtubei/v1/search?prettyPrint=false" \
  -H "Content-Type: application/json" -H "Origin: https://music.youtube.com" \
  -d "{\"context\":$innertube_context,\"query\":\"Daft Punk Get Lucky\",\"params\":\"EgWKAQIIAWoQEAMQBBAJEAoQBRAQEBUQEQ==\"}" |
  jq '{results: [.. | objects | select(has("musicResponsiveListItemRenderer")) | {musicResponsiveListItemRenderer: (.musicResponsiveListItemRenderer | {playlistItemData, flexColumns})}][:5]}' \
  > "$out/ytmusic_search.json"

curl -fsS -X POST "https://music.youtube.com/youtubei/v1/player?prettyPrint=false" \
  -H "Content-Type: application/json" -H "Origin: https://music.youtube.com" \
  -d "{\"context\":$innertube_context,\"videoId\":\"4D7u5KF7SP8\"}" |
  jq '{videoDetails: (.videoDetails | {videoId, title, author, lengthSeconds, thumbnail})}' \
  > "$out/ytmusic_player.json"

echo "recorded fixtures in $out"

#!/usr/bin/env bash
# Records real Tidal API responses into internal/music/testdata/tidal_*.json.
# Needs TIDAL_CLIENT_ID and TIDAL_CLIENT_SECRET in the environment; they are only
# sent to Tidal and never printed or written to disk. Quote the values when
# exporting them: the secret can contain shell-special characters.
# tidal_token.json is hand-written (standard OAuth response).
set -euo pipefail

: "${TIDAL_CLIENT_ID:?set TIDAL_CLIENT_ID}"
: "${TIDAL_CLIENT_SECRET:?set TIDAL_CLIENT_SECRET}"

out="$(dirname "$0")/../internal/music/testdata"
api="https://openapi.tidal.com/v2"
query="${1:-Velogic Go Back}"
encoded_query="$(jq -rn --arg q "$query" '$q|@uri')"

echo "credential lengths: id=${#TIDAL_CLIENT_ID} secret=${#TIDAL_CLIENT_SECRET}"
token_response="$(curl -sS -w '\n%{http_code}' -u "$TIDAL_CLIENT_ID:$TIDAL_CLIENT_SECRET" \
  -d grant_type=client_credentials https://auth.tidal.com/v1/oauth2/token)"
token_status="${token_response##*$'\n'}"
token_body="${token_response%$'\n'*}"
if [ "$token_status" != 200 ]; then
  echo "token request failed: HTTP $token_status"
  echo "$token_body" | jq -r 'del(.access_token)' 2>/dev/null || echo "$token_body"
  exit 1
fi
token="$(echo "$token_body" | jq -r .access_token)"

record() { # name path
  local status
  status="$(curl -sS -o "$out/$1.json" -w '%{http_code}' -H "Authorization: Bearer $token" \
    -H 'Accept: application/vnd.api+json' "$api$2")"
  echo "$status  $2  -> $1.json"
}

record tidal_track "/tracks/190405576?countryCode=US&include=artists"
record tidal_isrc "/tracks?countryCode=US&filter%5Bisrc%5D=USQX91300108&include=artists"
# The query is a filter parameter; the older /searchResults/{query} path form is rejected.
record tidal_search "/searchResults?countryCode=US&filter%5Bquery%5D=$encoded_query&include=tracks,tracks.artists"
echo "done; fixtures are in $out"

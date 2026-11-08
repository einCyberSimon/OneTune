# Onetune

Share a song from any streaming app as one link that opens in the recipient's own service. No accounts, no tracking.

- `shared/`, `androidApp/`, `iosApp/`: Kotlin Multiplatform app. Sharing builds `https://<domain>/<platform>/track/<id>` offline; opening a link looks it up on the resolver.
- `server/`: Go resolver that maps a track to the other services and renders the landing page.
- `deploy/helm/onetune`: Helm chart (domain, Tidal secret and Android signing fingerprint are values).
- `testvectors/links.json`: URL parsing cases shared by the Go and Kotlin parsers.

## Development loop

`make help` lists all targets.

```
make server      # resolver on :8080 (set TIDAL_CLIENT_ID / TIDAL_CLIENT_SECRET to enable Tidal)
make reverse     # adb reverse tcp:8080, so the debug app reaches the local server
make install     # or run the `androidApp` configuration in Android Studio
make share       # simulate sharing a Spotify link (SHARE_URL=... to change)
make open        # simulate tapping a resolver link (OPEN_PATH=apple/track/617154366)
make logcat
```

Debug builds look links up at `http://localhost:8080` (cleartext allowed for `localhost` in debug only); release builds use `https://<domain>`. Shared links always use the real domain. App Links are not verified for debug builds, so `make open` starts `OpenLinkActivity` explicitly. Pick a preferred service in the app first, otherwise links open in the browser.

Debugging: run `androidApp` with the debug button in Android Studio. For `ShareActivity`/`OpenLinkActivity`, which live only a moment, use *Attach Debugger to Android Process* or set the breakpoint and trigger `make share` / `make open`. For the server use `dlv debug ./server` or your Go IDE.

## Tests

```
make test          # everything
make test-server   # go test -race
make test-shared   # Kotlin link parser against testvectors/links.json
make lint
```

Server tests never touch the network:
- `internal/music/providers_test.go` runs each provider against recorded responses in `internal/music/testdata`. Re-record them with `make fixtures` when an upstream format changes, and the Tidal ones with `make tidal-fixtures` (needs `TIDAL_CLIENT_ID` / `TIDAL_CLIENT_SECRET`, quoted when exported). Only `tidal_token.json` is hand-written.
- `internal/resolve` and `internal/web` use `musictest.Fake`, a controllable provider, to cover caching, request sharing, the concurrency cap, escaping and headers.

When you add a platform, add its URL forms to `testvectors/links.json` (both parsers read it), a recorded fixture and a provider test.

## Deploy

```
helm upgrade --install onetune deploy/helm/onetune \
  --set domain=unitune.eincybersimon.de \
  --set tidalSecretName=tidal-credentials \
  --set android.certSha256=<release signing SHA-256>
```

The container image is built from `server/Dockerfile`. Turn off or anonymize ingress access logs if you want to keep the "no IP addresses stored" promise.

## License

0BSD

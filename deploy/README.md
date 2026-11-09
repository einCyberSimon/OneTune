# Deploy

CI publishes the server image to `ghcr.io/eincybersimon/onetune-server` (built from [`server/Dockerfile`](../server/Dockerfile)), tagged `main`, with the release version (e.g. `1.2.3`) and with the commit SHA.

## Kubernetes (Helm)

`helm/onetune`: domain, Tidal secret and Android signing fingerprint are values.

```
helm upgrade --install onetune helm/onetune \
  --set image.tag=<release version, e.g. 1.2.3> \
  --set domain=anytune.eincybersimon.de \
  --set tidalSecretName=tidal-credentials \
  --set android.certSha256=<release signing SHA-256>
```

## Docker Compose

`compose` runs the published image behind Caddy for a single host. Caddy gets a Let's Encrypt certificate for `DOMAIN` automatically (ports 80 and 443 must be reachable):

```
cd compose
cp .env.example .env   # set DOMAIN, optionally the Tidal credentials
docker compose up -d
```

Caddy only logs errors, not requests.

## Using a self-hosted server in the app

Enter `https://<domain>` under Settings, Experimental. Links on a self-hosted domain open in the browser unless the app is built with `-Ponetune.linkHost=<domain>` (and `ANDROID_CERT_SHA256` set to your signing key).

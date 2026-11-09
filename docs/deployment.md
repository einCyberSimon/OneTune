# Deployment

You can self-host the server. Note, that for self-hosted servers, you need to build the APP yourself. Otherwise, Onetune cannot open your links.
We provide descriptions for Kubernetes and Docker.

The server's image is built from [`server/Dockerfile`](../server/Dockerfile). The CI publishes the server's image to `ghcr.io/eincybersimon/onetune-server` and tags it with the release and the commit SHA.

## Kubernetes (Helm)

`deploy/helm/onetune`: domain, Tidal secret and Android signing fingerprint are values.

```
helm upgrade --install onetune deploy/helm/onetune \
  --set image.tag=<release version, e.g. 1.2.3> \
  --set domain=anytune.eincybersimon.de \
  --set tidalSecretName=tidal-credentials \
  --set android.certSha256=<release signing SHA-256>
```

## Docker Compose

`deploy/compose` runs the published image behind Caddy for a single host. Caddy gets a certificate from Let's Encrypt for `DOMAIN` automatically. Ports 80 and 443 must be reachable.

```
cd deploy/compose
cp .env.example .env   # set DOMAIN, optionally the Tidal credentials
docker compose up -d
```

Caddy only logs errors, not requests.

## Using a self-hosted server in the app

Enter `https://<domain>` under Settings, Experimental. Links on a self-hosted domain open in the browser unless the app is built with `-Ponetune.linkHost=<domain>` (and `ANDROID_CERT_SHA256` set to your signing key).

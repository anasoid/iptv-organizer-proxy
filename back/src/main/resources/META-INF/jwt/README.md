# JWT RSA Keys — Developer Placeholders Only

The `privateKey.pem` and `publicKey.pem` files in this directory are **placeholder keys
for local development and automated tests only**.

⚠️ **They are NOT used in Docker/production deployments.**

## How production keys work

When the Docker container starts, `docker-entrypoint.sh` generates a fresh RSA-2048
key pair and stores it in the `/app/data/` volume:

```
/app/data/privateKey.pem   ← signing key (chmod 600)
/app/data/publicKey.pem    ← verification key
```

Quarkus is pointed to these runtime keys via environment variables set in the entrypoint:

```bash
export SMALLRYE_JWT_SIGN_KEY_LOCATION=file:/app/data/privateKey.pem
export MP_JWT_VERIFY_PUBLICKEY_LOCATION=file:/app/data/publicKey.pem
```

This means:
- Every fresh deployment gets its own unique key pair.
- Pulling the Docker image gives an attacker zero usable keys.
- The keys survive container restarts (volume-persisted) so existing tokens stay valid.

## Supplying your own keys

Mount your keys into the data volume before starting:

```yaml
volumes:
  - /path/to/your/privateKey.pem:/app/data/privateKey.pem:ro
  - /path/to/your/publicKey.pem:/app/data/publicKey.pem:ro
```

Or use Docker secrets combined with `SMALLRYE_JWT_SIGN_KEY_LOCATION` /
`MP_JWT_VERIFY_PUBLICKEY_LOCATION` env vars.

## Regenerating dev keys

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out privateKey.pem
openssl pkey -in privateKey.pem -pubout -out publicKey.pem
```


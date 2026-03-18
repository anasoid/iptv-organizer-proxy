#!/bin/bash
set -e
echo "Starting IPTV Organizer Proxy - Quarkus Native"
# Create data directory if it doesn't exist
mkdir -p /app/data /logs

# ---------------------------------------------------------------------------
# JWT RSA key pair — generated once per deployment, stored in the data volume.
# This ensures:
#   1. Every fresh deployment gets unique keys (image does NOT contain prod keys).
#   2. Keys survive container restarts (volume-persisted) so existing tokens remain valid.
#   3. Operators can supply their own keys by mounting files to
#      /app/data/privateKey.pem and /app/data/publicKey.pem before starting.
# ---------------------------------------------------------------------------
if [ ! -f /app/data/privateKey.pem ] || [ ! -f /app/data/publicKey.pem ]; then
    echo "Generating JWT RSA-2048 key pair (first boot)..."
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
        -out /app/data/privateKey.pem 2>/dev/null
    openssl pkey -in /app/data/privateKey.pem -pubout \
        -out /app/data/publicKey.pem 2>/dev/null
    chmod 600 /app/data/privateKey.pem
    chmod 644 /app/data/publicKey.pem
    echo "JWT keys generated and stored in /app/data/"
else
    echo "Using existing JWT keys from /app/data/"
fi

# Override SmallRye JWT key locations so Quarkus uses the runtime-generated keys
# instead of the placeholder keys bundled in the binary.
export SMALLRYE_JWT_SIGN_KEY_LOCATION=file:/app/data/privateKey.pem
export MP_JWT_VERIFY_PUBLICKEY_LOCATION=file:/app/data/publicKey.pem

# Default configuration
export QUARKUS_HTTP_PORT=${QUARKUS_HTTP_PORT:-8080}
export QUARKUS_DATASOURCE_DB_KIND=${QUARKUS_DATASOURCE_DB_KIND:-sqlite}
export QUARKUS_DATASOURCE_JDBC_URL=${QUARKUS_DATASOURCE_JDBC_URL:-jdbc:sqlite:/app/data/app.sqlite}

echo "Starting native application on port $QUARKUS_HTTP_PORT..."
# Note: JAVA_OPTS does not apply to native binaries (no JVM).
# Native memory is fixed at compile time; use Quarkus config properties instead.
exec /work/application -Dquarkus.http.host=0.0.0.0


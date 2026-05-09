#!/bin/sh
set -e

echo "Starting IPTV Organizer Proxy - Quarkus Native"
mkdir -p /app/data /logs

export QUARKUS_HTTP_PORT=${QUARKUS_HTTP_PORT:-8080}
export QUARKUS_DATASOURCE_DB_KIND=${QUARKUS_DATASOURCE_DB_KIND:-sqlite}
export QUARKUS_DATASOURCE_JDBC_URL=${QUARKUS_DATASOURCE_JDBC_URL:-jdbc:sqlite:/app/data/app.sqlite}

echo "Starting native application on port $QUARKUS_HTTP_PORT..."
# Note: JAVA_OPTS does not apply — no JVM in native mode.
# Use NATIVE_STARTUP_OPTS for baseline args and NATIVE_EXTRA_OPTS for optional extras.
export NATIVE_STARTUP_OPTS=${NATIVE_STARTUP_OPTS:--Dquarkus.http.host=0.0.0.0}
export NATIVE_EXTRA_OPTS=${NATIVE_EXTRA_OPTS:-}

exec /work/iptv-organizer ${NATIVE_STARTUP_OPTS} ${NATIVE_EXTRA_OPTS}


#!/bin/bash
set -e
echo "Starting IPTV Organizer Proxy - Quarkus Backend"
# Create data directory if it doesn't exist
mkdir -p /app/data /logs


# Default configuration
export QUARKUS_HTTP_PORT=${QUARKUS_HTTP_PORT:-8080}
export QUARKUS_DATASOURCE_DB_KIND=${QUARKUS_DATASOURCE_DB_KIND:-sqlite}
export QUARKUS_DATASOURCE_JDBC_URL=${QUARKUS_DATASOURCE_JDBC_URL:-jdbc:sqlite:/app/data/app.sqlite}
echo "Starting Quarkus application on port $QUARKUS_HTTP_PORT..."
# JVM heap — override via JAVA_OPTS environment variable
JAVA_OPTS=${JAVA_OPTS:--Xms16m -Xmx64m -XX:+UseG1GC}
echo "JAVA_OPTS:  ${JAVA_OPTS} "
# Start Quarkus application with memory optimization
exec java \
  ${JAVA_OPTS} \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -jar /app/quarkus-run.jar

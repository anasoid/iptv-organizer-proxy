# Multi-stage Dockerfile for IPTV Organizer Proxy - Quarkus Java Backend
# Stage 1: Build the Java application
# Multi-stage Dockerfile for IPTV Organizer Proxy - Quarkus Java Backend
# Stage 1: Build the Java application
# Using official Gradle image to avoid downloading Gradle on each build
FROM gradle:9.2.1-jdk21 AS builder

WORKDIR /workspace

# Copy Gradle build files first (layer caching: dependencies change less often than source)
COPY back/gradle.properties /workspace/
COPY back/*.gradle /workspace/
COPY back/gradle/ /workspace/gradle/

# Copy source code (includes pre-built admin at src/main/resources/META-INF/resources/admin/)
# Run `cd admin && npm run build:java` before building the image
COPY back/src /workspace/src
COPY back/pmd-rules.xml /workspace/

# Build the application (JAR)
# Using Quarkus build for optimized output
# GRADLE_USER_HOME is set by the gradle image — no download needed
RUN gradle build -DskipTests -Dquarkus.package.jar.type=fast-jar --stacktrace  --console=plain --no-daemon \
    -x pmdMain -x pmdTest -x spotlessCheck -x spotlessJavaCheck

# Stage 2: Runtime image - minimal JVM with Quarkus app
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="IPTV Organizer Proxy"
LABEL description="IPTV Organizer Proxy - Quarkus Java Backend"

WORKDIR /app

# Install minimal dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# Create non-root user for security
RUN useradd -m -u 1000 appuser && \
    mkdir -p /app/data /logs && \
    chown -R appuser:appuser /app /logs

# Copy built JAR from builder stage
COPY --from=builder /workspace/build/quarkus-app/quarkus-run.jar /app/
COPY --from=builder /workspace/build/quarkus-app/lib /app/lib/
COPY --from=builder /workspace/build/quarkus-app/app /app/app/
COPY --from=builder /workspace/build/quarkus-app/quarkus /app/quarkus/

# Copy entrypoint script
COPY docker/docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Entrypoint
ENTRYPOINT ["/app/docker-entrypoint.sh"]


# ─────────────────────────────────────────────────────────────────────
# Build stage
# ─────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

RUN apk add --no-cache maven

# Layer: download dependencies first (cache-friendly)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Layer: build application
COPY src src/
RUN mvn clean package -DskipTests -B -q

# ─────────────────────────────────────────────────────────────────────
# Runtime stage
# ─────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for HEALTHCHECK
RUN apk add --no-cache curl && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup

# Copy built JAR
COPY --from=builder /build/target/bot-0.0.1-SNAPSHOT.jar app.jar

# Create certs directory (mount your private-key.pem here)
RUN mkdir -p certs && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# ── Health check ────────────────────────────────────────────────────
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

# ── JVM configuration ───────────────────────────────────────────────
# Use G1GC, limit heap to 75% of container memory, enable container support
ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

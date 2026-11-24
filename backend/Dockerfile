# syntax=docker/dockerfile:1.7

# =============================================================================
# Stage 1: Build Spring Boot Fat JAR (Maven + Temurin JDK 21)
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -DskipTests && \
    find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -exec cp {} /workspace/app.jar \;

# =============================================================================
# Stage 2: Minimal Non-Root Runtime (Temurin JRE 21 Alpine)
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache tzdata && \
    addgroup -S accesolibre && \
    adduser -S -G accesolibre accesolibre

WORKDIR /app

COPY --from=builder --chown=accesolibre:accesolibre /workspace/app.jar /app/app.jar

USER accesolibre

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

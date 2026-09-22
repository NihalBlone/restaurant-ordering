# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS frontend
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY deploy/frontend.ref deploy/frontend.ref
COPY scripts/fetch-frontend.sh scripts/fetch-frontend.sh
# An immutable commit, never a moving branch; no GitHub token is required for this public UI repo.
RUN bash scripts/fetch-frontend.sh /build/frontend
WORKDIR /build/frontend
RUN npm ci --no-audit --no-fund && npm test && npm run build

FROM maven:3-eclipse-temurin-26 AS backend
WORKDIR /build/backend
COPY pom.xml ./
COPY .mvn/ .mvn/
RUN mvn -B -s .mvn/settings-public.xml dependency:go-offline --no-transfer-progress
COPY src/ src/
COPY --from=frontend /build/frontend/dist/ src/main/resources/static/
RUN mvn -B -s .mvn/settings-public.xml package --no-transfer-progress

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends gosu curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app \
    && mkdir -p /app /var/data/menu-images && chown -R app:app /var/data
WORKDIR /app
COPY --from=backend /build/backend/target/restaurant-ordering-*.jar /app/application.jar
COPY deploy/entrypoint.sh /usr/local/bin/restaurant-entrypoint
RUN chmod 755 /usr/local/bin/restaurant-entrypoint
ENV SPRING_PROFILES_ACTIVE=prod \
    APP_MENU_IMAGE_DIRECTORY=/var/data/menu-images \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --silent "http://127.0.0.1:${PORT}/actuator/health/readiness" || exit 1
# Root initializes only the volume directory; the entrypoint runs Java as uid 10001.
ENTRYPOINT ["/usr/local/bin/restaurant-entrypoint"]
CMD ["java", "-jar", "/app/application.jar"]

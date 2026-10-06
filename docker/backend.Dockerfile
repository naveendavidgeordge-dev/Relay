# Relay backend: one image, two roles (API: SPRING_PROFILES_ACTIVE=api; worker: WORKER_ONLY=true).
# Build context: repository root (the catalog/seeds are copied from /data at build time).

FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY data ./data
COPY backend ./backend
RUN gradle -p backend --no-daemon bootJar -x test -Prelay.fixturesDir=/src/data

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S relay && adduser -S relay -G relay && apk add --no-cache curl
WORKDIR /app
COPY --from=build /src/backend/build/libs/relay.jar /app/relay.jar
USER relay
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
  CMD [ "$WORKER_ONLY" = "true" ] || curl -fsS http://localhost:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/relay.jar"]

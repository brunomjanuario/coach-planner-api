# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependencies layer first, so an unchanged build.gradle.kts lets Docker reuse this layer's cache.
COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

COPY src src
RUN ./gradlew --no-daemon bootJar

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar
# A fixed numeric UID/GID needs no groupadd/useradd, which not every minimal
# base image ships — chown here, not `adduser`, is what makes this portable.
RUN chown 1000:1000 app.jar
USER 1000:1000

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

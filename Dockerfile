FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY observability-spring-boot-starter/build.gradle ./observability-spring-boot-starter/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet dependencies || true

COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

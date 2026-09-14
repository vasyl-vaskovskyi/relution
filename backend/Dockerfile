# syntax=docker/dockerfile:1
# Images, flags and health check: docs/operations/deployment.md

FROM eclipse-temurin:25.0.4_7-jdk-resolute AS build
WORKDIR /workspace

# Wrapper and build scripts first, so the Gradle download stays cached
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --version

COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar \
    && java -Djarmode=tools -jar build/libs/appstore-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25.0.4_7-jre-resolute
WORKDIR /app

RUN groupadd --system appstore \
    && useradd --system --gid appstore --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin appstore

# Layers ordered from least to most frequently changed
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER appstore
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# Build with the Gradle wrapper so the project selects its exact Gradle version.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-noble@sha256:589ff4cc3f71aab462e7048a47a0d10edf57fbccde3fceea2281e610bf5880b4 AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY certs/rds-global-bundle.pem ./certs/rds-global-bundle.pem
RUN echo 'fe45bbebf92ad3e27a583bbb2ddd1553c521ed4d49af5514dc0a40372ea5395c  certs/rds-global-bundle.pem' | sha256sum --check --status
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon bootJar -x test \
    && cp "$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" /workspace/app.jar

FROM eclipse-temurin:25-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab
WORKDIR /app

COPY --from=build /workspace/certs/rds-global-bundle.pem /etc/ssl/certs/aws-rds-global-bundle.pem
COPY --from=build --chown=10001:10001 /workspace/app.jar /app/app.jar
USER 10001:10001

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

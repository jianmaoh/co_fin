# syntax=docker/dockerfile:1

# ---- build stage: full JDK + Maven wrapper ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# The cache mount keeps downloaded dependencies between builds,
# so only the first build has to fetch them.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests package \
    && cp target/co-fin-*.jar app.jar

# ---- runtime stage: JRE only, smaller and with no build tools ----
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

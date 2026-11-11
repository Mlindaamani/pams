# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl && addgroup -S pams && adduser -S pams -G pams
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract \
    && cp -R spring-boot-loader/. . \
    && cp -R dependencies/. . \
    && cp -R snapshot-dependencies/. . \
    && cp -R application/. . \
    && rm -rf spring-boot-loader dependencies snapshot-dependencies application app.jar \
    && chown -R pams:pams /app
USER pams
EXPOSE 8080 9090
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=20s --timeout=5s --retries=5 --start-period=40s \
  CMD curl -fsS http://127.0.0.1:9090/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

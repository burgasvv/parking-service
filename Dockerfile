
FROM gradle:9.2.1 AS build
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle buildFatJar --no-daemon

FROM bellsoft/liberica-openjdk-alpine:24 AS prod
EXPOSE 9000
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
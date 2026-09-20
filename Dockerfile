FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG SERVICE
COPY . .
RUN mvn -B -pl "${SERVICE}" -am package -DskipTests && cp "${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar" /app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=app:app /app.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

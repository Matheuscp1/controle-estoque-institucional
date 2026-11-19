FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src/main src/main
# Tests run before packaging, outside Docker's image build (Testcontainers needs Docker).
RUN mvn -B -Dmaven.test.skip=true package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/logs /data/imports && chown -R 10001:10001 /app/logs /data/imports
ENV LOG_DIRECTORY=/app/logs
ENV IMPORTS_DIRECTORY=/data/imports
COPY --from=build /app/target/controle-estoque-institucional-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

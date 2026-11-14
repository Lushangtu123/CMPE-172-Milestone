FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,id=tutorconnect-maven,target=/root/.m2 mvn -B dependency:go-offline
COPY scripts/setup-rds-truststore.sh ./scripts/setup-rds-truststore.sh
RUN sh scripts/setup-rds-truststore.sh /build/rds-ca.p12
COPY src ./src
COPY docker/Healthcheck.java ./docker/Healthcheck.java
RUN --mount=type=cache,id=tutorconnect-maven,target=/root/.m2 mvn -B -DskipTests package && javac -d health docker/Healthcheck.java

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/tutorconnect-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=build --chown=10001:10001 /build/health /app/health
COPY --from=build --chown=10001:10001 /build/rds-ca.p12 /app/rds-ca.p12
COPY --chown=10001:10001 docker/start.sh /app/start.sh
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=4s --start-period=45s --retries=5 CMD ["java", "-cp", "/app/health", "Healthcheck"]
ENTRYPOINT ["sh", "/app/start.sh"]

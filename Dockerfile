FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 wallet
WORKDIR /app
COPY --from=build /workspace/ip-breaker-wallet-bootstrap/target/ip-breaker-wallet-bootstrap-*.jar app.jar
USER wallet
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]


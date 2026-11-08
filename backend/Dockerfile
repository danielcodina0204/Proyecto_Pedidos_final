FROM maven:3.9.16-eclipse-temurin-25 AS compilacion
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:25.0.5_8-jre-alpine
WORKDIR /servicio
COPY --from=compilacion /app/target/pedidos.jar app.jar
RUN addgroup -S servicio && adduser -S servicio -G servicio
USER servicio
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/order-service-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu AS build
RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN mvn -pl services/inventory-service -am clean package -DskipTests

FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu
WORKDIR /app
COPY --from=build /workspace/services/inventory-service/target/inventory-service-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

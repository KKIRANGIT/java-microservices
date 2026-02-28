FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu AS build
RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN mvn -pl services/notification-service -am clean package -DskipTests

FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu
WORKDIR /app
COPY --from=build /workspace/services/notification-service/target/notification-service-1.0.0.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

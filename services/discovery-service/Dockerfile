FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu AS build
RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN mvn -pl services/discovery-service -am clean package -DskipTests

FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu
WORKDIR /app
COPY --from=build /workspace/services/discovery-service/target/discovery-service-1.0.0.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

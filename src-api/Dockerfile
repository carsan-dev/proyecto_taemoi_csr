FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean install -DskipTests

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/Proyecto_TaeMoi_csr-0.1.0-SNAPSHOT.jar app.jar
COPY wait-for-it.sh .
RUN chmod +x wait-for-it.sh
EXPOSE 8080
ENTRYPOINT ["./wait-for-it.sh", "database:3306", "--", "java", "-jar", "app.jar"]
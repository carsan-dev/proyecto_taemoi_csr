FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -Dspring-boot.build-image.imageName=taemoi-api

# Extract layered JAR
RUN java -Djarmode=layertools -jar target/Proyecto_TaeMoi_csr-0.2.0-SNAPSHOT.jar extract

# Final stage with JRE (much smaller than JDK)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install bash for wait-for-it script
RUN apk add --no-cache bash

# Copy layers from build stage (better caching)
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./

# Copy wait-for-it script
COPY wait-for-it.sh .
RUN chmod +x wait-for-it.sh

# Add healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "./wait-for-it.sh database:3306 -- java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
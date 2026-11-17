# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with the Maven wrapper on a JDK 25 (LTS) image ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage: slim JRE 25 (LTS) image, non-root user ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Copy Spring Boot layers from least to most frequently changing
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

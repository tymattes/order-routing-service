# order-routing-service

Routes multi-item DME orders to suppliers by feasibility, customer experience, quality, and geographic preference.

## Overview

_TODO_

## Routing criteria

- **Feasibility** — _TODO_
- **Customer experience** — _TODO_
- **Quality** — _TODO_
- **Geographic preference** — _TODO_

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 25 (LTS) |
| Framework | Spring Boot 4.1.2 (Spring Framework 7.0.10) |
| Web | Spring Web MVC on embedded Tomcat 11, port 8080 |
| Operations | Spring Boot Actuator (`health`, liveness and readiness probes) |
| Build | Maven 3.10.1 via the Maven Wrapper (`./mvnw`), packaged as an executable jar |
| Configuration | YAML (`src/main/resources/application.yaml`) |
| Testing | JUnit 6, Spring Boot Test with MockMvc |
| API testing | Postman collection, runnable with Newman |
| Containers | Multi-stage Docker build: `eclipse-temurin:25-jdk` to build, `eclipse-temurin:25-jre` to run (non-root user, layered jar) |

Base package: `com.tylermatteson.orderrouting`. The project was scaffolded with [Spring Initializr](https://start.spring.io/).

## Build & run

```bash
mvn clean install 
mvn spring-boot:run
```

### Docker

```bash
docker build -t order-routing-service .
docker run --rm -p 8080:8080 order-routing-service
```

Health check: `GET http://localhost:8080/actuator/health` (also `/actuator/health/liveness` and `/actuator/health/readiness`).

### API tests (Postman)

The Postman collection and local environment live in `postman/`. Import both into Postman, or run them from the CLI with Newman while the app is running:

```bash
npx newman run postman/order-routing-service.postman_collection.json \
  -e postman/local.postman_environment.json
```

## Development workflow

`main` is protected: all changes land via pull request; force pushes are blocked.

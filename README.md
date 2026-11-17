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

Java 25 · Spring Boot 4.1.2 · Maven (wrapper included) · Docker

## Build & run

```bash
./mvnw clean verify        # build + tests
./mvnw spring-boot:run     # run locally on :8080
```

### Docker

Multi-stage build: `eclipse-temurin:25-jdk-resolute` compiles, `eclipse-temurin:25-jre-resolute`
(Java 25 LTS on Ubuntu 26.04 LTS) runs a layered Spring Boot jar as a non-root user.

```bash
docker build -t order-routing-service .
docker run --rm -p 8080:8080 order-routing-service
curl localhost:8080/actuator/health
```

JVM flags can be overridden with `-e JAVA_OPTS="..."`.

## Development workflow

`main` is protected: all changes land via pull request; force pushes are blocked.

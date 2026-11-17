# order-routing-service

Routes multi-item DME orders to suppliers by feasibility, customer experience, quality, and geographic preference.

## Overview

_TODO_

## Routing criteria

- **Feasibility** — _TODO_
- **Customer experience** — _TODO_
- **Quality** — _TODO_
- **Geographic preference** — _TODO_

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

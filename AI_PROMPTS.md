# AI Prompts

A log of significant prompts given to AI coding assistants while building this project, in the
order they were used.

---

## 1. Create the GitHub repository

**Tool:** Claude Code (Claude Opus 5.5)

```text
create a public github repo with gh. wait for my ok before creating it.

- name: order-routing-service
- description: "Routes multi-item DME orders to suppliers by feasibility,
  customer experience, quality, and geographic preference."
- commit a java/maven .gitignore
- commit a README stub
- protect main: require a pr
- protect main: block force pushes
```

---

## 2. Project scaffold 

**Assistant:** Claude Code (Claude Opus 5.5)

```text
In a branch called project-scaffold

Lets use https://start.spring.io/ to scaffold this project

- Java 25
- Spring Boot 4.1.2(latest release)
- Maven
- group: com.tylermatteson
- artifact: order-routing-service
- pacakge: com.tylermatteson.orderrouting
- jar
- configuration: yaml
- description: Routes multi-item DME orders to suppliers by feasibility, customer experience, quality, and geographic preference.

Dockerize this application with a multi-stage dockerfile using the latest long term support images running on port 8080

Create an empty AI_PROMPTS.md file at the project root with a header significant prompts given to AI coding assistants while building this project
```

## 2. Actuator health endpoint and Postman suite

**Assistant:** Claude Code (Claude Opus 5.5)

```text
lets setup an actuator health endpoint and a postman test suite
```

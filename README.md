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

The image includes `data/` at `/app/data/`. To use different data without rebuilding, mount a directory and point `DATA_DIR` at it:

```bash
docker run --rm -p 8080:8080 \
  -v "$(pwd)/my-data:/mnt/data:ro" \
  -e DATA_DIR=/mnt/data \
  order-routing-service
```

Health check: `GET http://localhost:8080/actuator/health` (also `/actuator/health/liveness` and `/actuator/health/readiness`).

### API tests (Postman)

The Postman collection and local environment live in `postman/`. Import both into Postman, or run them from the CLI with Newman while the app is running:

```bash
npx newman run postman/order-routing-service.postman_collection.json \
  -e postman/local.postman_environment.json
```

## Reference data

At startup the service loads `products.csv` and `suppliers.csv` from the directory set by `DATA_DIR` (property `app.data-dir`, default `data/` relative to the working directory). Loading fails fast: the application won't start if a file is missing or unreadable, a required column is missing, a row has the wrong number of columns, or a row is invalid. Row errors name the file, line number, row id and the offending raw value.

Both files use the same format:

- Standard CSV (RFC 4180), so quoted values may contain commas. Files are read as UTF-8, and LF and CRLF line endings both work.
- The first row is the header. Columns are found by header name, so their order doesn't matter and extra columns are ignored.
- Every row must have the same number of columns as the header.
- Line numbers in errors count the header as line 1.

### Product and supplier codes

`product_code` and `supplier_id` are loaded exactly as written, with no trimming or case change, and lookups are exact matches. Normalizing them at load time wasn't necessary: every code in the source data is already trimmed and upper-case (for example `WC-STD-001`, `SUP-001`).

Where a request carries a product code or supplier id, the request side will trim and upper-case it before lookup. That is a well-established pattern, so it will be added in the request handling as each endpoint needs it rather than in the loaders.

### Categories

Both files use the same closed set of 24 categories. Values are trimmed and lower-cased before matching, so `CPAP`, ` cpap ` and `cpap` are all the same category:

`ankle brace`, `back brace`, `blood pressure monitor`, `cane`, `cervical collar`, `commode`, `compression stockings`, `cpap`, `cpm machine`, `crutches`, `glucose meter`, `heating pad`, `hospital bed`, `ice machine`, `knee scooter`, `nebulizer`, `oxygen`, `patient lift`, `rollator`, `shower chair`, `tens unit`, `traction device`, `walker`, `wheelchair`.

### Products (`products.csv`)

Required columns: `product_code`, `product_name`, `category`.

- `product_code` is used exactly as written (see [Product and supplier codes](#product-and-supplier-codes)). A blank code fails loading.
- `product_name` is kept as-is.
- `category` must match one of the [known categories](#categories). A blank or unknown category fails loading.
- A repeated `product_code` with the same category is skipped with a WARN log, and the first row is kept. A repeated code with a different category fails loading, and the error names both lines.
- Products are looked up by code only.

### Suppliers (`suppliers.csv`)

Required columns: `supplier_id`, `suplier_name` (spelled as in the source data), `service_zips`, `product_categories`, `customer_satisfaction_score`, `can_mail_order?`.

- `supplier_id` is used exactly as written (see [Product and supplier codes](#product-and-supplier-codes)). A blank id fails loading, and so does any repeated id (the error names both lines).
- `suplier_name` is kept as-is.
- `product_categories` is a comma-separated list, so it must be quoted when it has more than one value. Each value must match one of the [known categories](#categories). An unknown or empty value, or an empty list, fails loading. A category listed twice counts once.
- `customer_satisfaction_score` is trimmed. `no ratings yet` (any case) means the supplier has no score. Otherwise it must be a plain decimal number (`9`, `9.0` and `7.8` are all valid) between 1 and 10 inclusive. Values outside that range fail loading rather than being clamped.
- `can_mail_order?` is trimmed and must be `y` or `n` (any case).
- `service_zips` is a comma-separated list of tokens, and spaces around each token are ignored. An empty list fails loading. Each token is a zip (1-5 digits) or a range `start-end` (1-5 digits on each side). Zips are compared as integers, so 4-digit values with the leading zero dropped work: `2110` is `02110`, and `2164-2213` is `02164-02213`. Empty tokens, malformed tokens, and inverted ranges fail loading. Overlapping ranges and single zips inside a range are allowed.
- Order zips are normalized the same way before they are compared. ZIP+4 (`02110-1234`) uses the first five digits, and an invalid zip matches no supplier.
- A supplier covering every zip (for example `00100-99999`) is still only local coverage. It is unrelated to `can_mail_order?`.
- Suppliers can be looked up by id, or by category (results come back in file order).
- Rows `SUP-T001`..`SUP-T014` are routing test fixtures with placeholder zips such as `00001`. They are loaded like any other row.

## Development workflow

`main` is protected: all changes land via pull request; force pushes are blocked.

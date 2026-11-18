# order-routing-service

Routes multi-item DME orders to suppliers, optimizing (in order) for feasibility, fewest shipments, supplier rating, and local over mail-order fulfillment. Exposes `POST /api/route`.

## Run it

With Docker (no local Java needed):

```bash
docker build -t order-routing-service .
docker run --rm -p 8080:8080 order-routing-service
```

Or locally with Java 25:

```bash
./mvnw spring-boot:run
```

Try a sample order:

```bash
curl -s localhost:8080/api/route -H 'Content-Type: application/json' -d '{
  "order_id": "ORD-001", "customer_zip": "10015", "mail_order": false,
  "items": [ { "product_code": "WC-STD-001", "quantity": 1 }, { "product_code": "OX-PORT-024", "quantity": 1 } ]
}'
```

Health: `GET /actuator/health` (plus `/liveness` and `/readiness`).

## Test

```bash
./mvnw test
```

Unit tests cover supplier eligibility and plan selection; acceptance tests drive the endpoint through MockMvc against the real CSVs, including the three orders in `data/sample_orders.json`.

API tests against a running instance (Postman collection in `postman/`):

```bash
npx newman run postman/order-routing-service.postman_collection.json \
  -e postman/local.postman_environment.json
```

## Routing logic

A *plan* is a set of suppliers that together can take every item. Each rule only breaks ties left by the ones before it.

1. **Feasibility.** A supplier can take an item if it carries the item's category and either serves the customer's zip (`local`) or the order allows mail order and the supplier ships by mail (`mail_order`). A supplier serving the zip counts as `local` even if it also mails. If any item has no feasible supplier, the order is infeasible, with one error per such item.
2. **Fewest shipments.** Only plans with the minimum number of suppliers are considered. If one supplier can take everything, it gets the whole order, even if a split would be better rated.
3. **Quality.** Plans with fewer unrated (`no ratings yet`) suppliers win. Among those, a plan's rating is the mean of its suppliers' scores, and plans more than `app.routing.rating-tolerance` (default `0.5`) below the best are dropped.
4. **Local preference.** Of the plans left, the one with more local suppliers wins. Because of the tolerance, local preference can outweigh a small rating gap but not a large one.
5. **Tie-break.** Higher rating, then lowest supplier ids, so results are deterministic.

Within the chosen plan, each item goes to the best plan supplier that can take it (local, then rated, then higher score, then lowest id).

## API

`POST /api/route` with `Content-Type: application/json`. Fields are snake_case; unknown fields (such as `priority` and `notes` in the samples) are ignored. Request and response shapes match the exercise spec:

```json
{ "feasible": true,
  "routing": [ { "supplier_id": "SUP-0636", "supplier_name": "Care Supply Corp #636",
                 "items": [ { "product_code": "WC-STD-001", "quantity": 1, "category": "wheelchair", "fulfillment_mode": "local" } ] } ] }
```

```json
{ "feasible": false, "errors": [ "Order must include at least one line item.", "Order must include a valid customer_zip." ] }
```

Shipments are ordered by where each supplier's first item appears in the request, and items keep request order.

`POST /api/route` always returns `200`; `feasible` tells you the outcome. Only an unexpected server error returns `500`.

| `feasible` | When |
|---|---|
| `true` | The order is valid and routable; `routing` lists the shipments. |
| `false` | The order is valid but some item can't be routed: `No supplier can fulfill product_code CODE (CATEGORY) for customer_zip ZIP.` per unroutable item. |
| `false` | The request is invalid: malformed JSON, wrong field type, missing or invalid field, or unknown `product_code`. `errors` lists every validation error (messages in `RouteErrors`). |
| `false` | `Content-Type` is missing or not `application/json`: `Content-Type must be application/json.` |

Validation: `order_id` required; `customer_zip` is 1-5 digits or ZIP+4; `mail_order` must be `true`/`false`; `items` must be non-empty, each with a known `product_code` (trimmed and upper-cased) and `quantity` ≥ 1.

## Data handling

`products.csv` and `suppliers.csv` are loaded once at startup from `DATA_DIR` (default `data/`; baked into the image at `/app/data`). Loading fails fast with the file, line and bad value if the data is invalid, so the service never runs on partially loaded data.

How the messy supplier data is interpreted:

- **`service_zips`**: mix of single zips and `start-end` ranges, compared as integers so a dropped leading zero still matches (`2110` = `02110`). ZIP+4 in requests uses the first five digits.
- **`customer_satisfaction_score`**: decimal 1-10, or `no ratings yet` (any case) for unrated. Out-of-range values fail loading rather than being clamped.
- **`product_categories`** and product `category`: trimmed and lower-cased, and must be one of the 24 known categories.
- **`can_mail_order?`**: `y`/`n`, any case. Nationwide zip coverage (for example `00100-99999`) is still local, not mail.
- Duplicate products with the same category are skipped with a warning; conflicting duplicates and duplicate supplier ids fail loading. The `suplier_name` header typo is matched as-is.
- Suppliers `SUP-T001`..`SUP-T014` are routing test fixtures with placeholder zips.

To use different data without rebuilding:

```bash
docker run --rm -p 8080:8080 -v "$(pwd)/my-data:/mnt/data:ro" -e DATA_DIR=/mnt/data order-routing-service
```

## Tech stack

Java 25, Spring Boot 4.1 (Web MVC, Actuator), Maven wrapper, JUnit 6 + MockMvc, multi-stage Docker build on `eclipse-temurin:25` (non-root, layered jar). Config lives in `src/main/resources/application.yaml`.

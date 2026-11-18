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

## 3. Actuator health endpoint and Postman suite

**Assistant:** Claude Code (Claude Opus 5.5)

```text
lets setup an actuator health endpoint and a postman test suite
```

---

## 4. Review products.csv for duplicate and inconsistent product codes

**Assistant:** Claude Code (Claude Opus 5.5)

```text
given the products.csv file in the data directory

review this data for duplications and inconsistencies

paying attention only to product_code and category 

we'll be loading this data into memory and I need to understand where data needs to be deduped, trimmed, or case modified so that there is no record colision by product code

are there any duplicate product_codes with different categories?
```

**Response:**

No product_code has two different categories. Even after normalizing case, every repeated code maps to the same category.

I looked only at `product_code` and `category` in `data/products.csv` (1,200 rows plus the header, 3 columns on every row, plain ASCII, no quotes or empty fields).

**1. Duplicate product codes: 5 codes, 10 rows, all exact repeats**

Each pair is an identical row: same code, name and category. Keeping the first and dropping the second loses nothing. After that you have 1,195 unique codes.

| product_code | lines |
|---|---|
| `HB-MAN-019-BAS` | 124, 174 |
| `WK-BAR-013-BAS` | 128, 130 |
| `CM-DROP-049-PLU` | 152, 191 |
| `CR-BAR-072-ECO` | 162, 163 |
| `WK-STD-009-PLU` | 175, 189 |

**2. product_code formatting: clean**

There's no leading or trailing whitespace and no lowercase, and every code uses only `A-Z`, `0-9` and `-`. So trimming or changing case won't create new collisions. It's still worth applying `strip().toUpperCase()` when loading, to protect against future edits.

**3. Category case is inconsistent**

| Variant | Rows |
|---|---|
| `cpap` / `CPAP` | 42 / 14 |
| `cpm machine` / `CPM machine` | 41 / 3 |

The other 22 categories are already lowercase. Lowercasing everything brings the list down to 24 distinct categories. There's no whitespace in any category.

**4. Line endings are CRLF (`\r\n`)**

If you parse by splitting on `\n`, a stray `\r` will stick to the end of `category` (e.g. `"walker\r"`), and those won't match the clean values. Either use a real CSV parser or trim each field.

**5. One code doesn't follow the prefix pattern (not a collision)**

Line 206, `CM-SENT-001,Sentinel CPM Machine,cpm machine`. Everywhere else, `CM-` means commode and CPM machines use `CPM-`. It doesn't collide with anything now. But if any of your logic infers category from the code prefix, this row will be misclassified, so check it with whoever owns the data.

**Loading rules that cover all of this:**
- Key on `product_code.trim().toUpperCase()`.
- Normalize category with `category.trim().toLowerCase()`.
- On a duplicate key: if the category matches, skip the row (and log it). If it doesn't match, fail loudly. That can't happen with today's data, but it protects you later.

`data/` is still untracked in git and sits at the repo root, not under `src/main/resources/data/`. So it won't be on the classpath or in the jar until you move it there.

---

## 5. Plan the product CSV loading and repository prompt

**Assistant:** Claude Code (Claude Opus 5.5) — /plan mode

```text
given this research output help me generate a prompt

Note: I'm going away from the suggested logic, we want to rely on an exact match for product code. I'm going away from the suggested logic, we want to rely on an exact match for product code.

on startup

we need to load Product csv data into an immutable Map<String, Product> by product_code

Data will be located in  DATA_DIR (default data/) as products.csv - if this is missing fail fast

use https://commons.apache.org/proper/commons-csv/ for parsing

using the rules: 
- Do not modify product_code when loading data, we want to rely on an exact match here as we don't want to infer a product incorrectly
- Normalize category with category.trim().toLowerCase(). - lets enumerate this
- On a duplicate key: if the category matches, skip the row (and log it). If it doesn't match, fail loudly. That can't happen with today's data, but it protects you later.

using the java respository design pattern(https://java-design-patterns.com/patterns/repository/) build the repository layer for product. for now we just need to access by code - return an optional here as an unknown code should be an expected outcome
```

**Response:**

Here's a prompt you can paste into a fresh session. It covers all your rules and pins down the details an agent would otherwise have to guess.

````text
On the data-loading branch, add product data loading and a product repository layer.

## Context
- Java 25, Spring Boot 4.1.2, Maven, package com.tylermatteson.orderrouting
- data/products.csv (repo root) has a header: product_code,product_name,category
- The file uses CRLF line endings. There are 1,200 rows: 5 product_codes appear twice as identical rows, and the categories "CPAP" and "CPM machine" use mixed case.

## Loading (at startup)
- Read products.csv from the directory given by DATA_DIR, defaulting to data/.
  Bind it as a property, for example app.data-dir: ${DATA_DIR:data/} in application.yaml,
  using @ConfigurationProperties.
- Fail fast: if the file is missing or unreadable, or a required header column is missing,
  the application context must fail to start with a clear error.
- Parse with Apache Commons CSV (org.apache.commons:commons-csv, latest stable release),
  using the header row for column names.
- Load into an immutable Map<String, Product> keyed by product_code (Map.copyOf or similar).

## Rules
- Do NOT modify product_code: no trim, no case change. Lookups are exact matches, because
  inferring the wrong product is worse than finding none. A blank product_code fails loading.
- Normalize category with category.trim().toLowerCase(), then map it to a Category enum.
  An unrecognized category fails loading. The enum covers exactly these 24 values:
  ankle brace, back brace, blood pressure monitor, cane, cervical collar, commode,
  compression stockings, cpap, cpm machine, crutches, glucose meter, heating pad,
  hospital bed, ice machine, knee scooter, nebulizer, oxygen, patient lift, rollator,
  shower chair, tens unit, traction device, walker, wheelchair
  (constants such as HOSPITAL_BED, each holding its label, plus a static lookup from label).
- Duplicate product_code:
  - Same normalized category: skip the row and log a WARN with the code and line number.
  - Different category: fail loading with an error that names the code and both line numbers.

## Repository layer (https://java-design-patterns.com/patterns/repository/)
- Product: a record with code, name, and Category.
- ProductRepository interface: Optional<Product> findByCode(String code).
  An unknown code is an expected outcome and returns Optional.empty(), not an exception.
- An in-memory implementation backed by the loaded map. Keep the CSV parsing in its own
  loader class so the repository doesn't depend on the file format.

## Tests
- Put fixture CSVs under src/test/resources/ and cover: a valid load, exact-match lookup
  (a code with different case or surrounding whitespace returns empty), an unknown code
  returning empty, a same-category duplicate being skipped, a different-category duplicate
  failing, an unknown category failing, a missing file failing, and CRLF input.
- Make sure ./mvnw clean verify passes.

## Docker
- The image doesn't contain data/ yet, so the container would now fail fast on startup.
  Copy data/ into the image (resolved against WORKDIR /app) and document in the README
  that DATA_DIR can point to a mounted volume instead.

Don't commit. Summarize the changes when you're done.
````
---

## 6. Product CSV loading and repository layer

**Assistant:** Claude Code (Claude Opus 5.5)

The prompt generated in step 5, run in a fresh session (without the trailing "Don't commit" line).

```text
On the data-loading branch, add product data loading and a product repository layer.

## Context
- Java 25, Spring Boot 4.1.2, Maven, package com.tylermatteson.orderrouting
- data/products.csv (repo root) has a header: product_code,product_name,category
- The file uses CRLF line endings. There are 1,200 rows: 5 product_codes appear twice as identical rows, and the categories "CPAP" and "CPM machine" use mixed case.

## Loading (at startup)
- Read products.csv from the directory given by DATA_DIR, defaulting to data/.
  Bind it as a property, for example app.data-dir: ${DATA_DIR:data/} in application.yaml,
  using @ConfigurationProperties.
- Fail fast: if the file is missing or unreadable, or a required header column is missing,
  the application context must fail to start with a clear error.
- Parse with Apache Commons CSV (org.apache.commons:commons-csv, latest stable release),
  using the header row for column names.
- Load into an immutable Map<String, Product> keyed by product_code (Map.copyOf or similar).

## Rules
- Do NOT modify product_code: no trim, no case change. Lookups are exact matches, because
  inferring the wrong product is worse than finding none. A blank product_code fails loading.
- Normalize category with category.trim().toLowerCase(), then map it to a Category enum.
  An unrecognized category fails loading. The enum covers exactly these 24 values:
  ankle brace, back brace, blood pressure monitor, cane, cervical collar, commode,
  compression stockings, cpap, cpm machine, crutches, glucose meter, heating pad,
  hospital bed, ice machine, knee scooter, nebulizer, oxygen, patient lift, rollator,
  shower chair, tens unit, traction device, walker, wheelchair
  (constants such as HOSPITAL_BED, each holding its label, plus a static lookup from label).
- Duplicate product_code:
  - Same normalized category: skip the row and log a WARN with the code and line number.
  - Different category: fail loading with an error that names the code and both line numbers.

## Repository layer (https://java-design-patterns.com/patterns/repository/)
- Product: a record with code, name, and Category.
- ProductRepository interface: Optional<Product> findByCode(String code).
  An unknown code is an expected outcome and returns Optional.empty(), not an exception.
- An in-memory implementation backed by the loaded map. Keep the CSV parsing in its own
  loader class so the repository doesn't depend on the file format.

## Tests
- Put fixture CSVs under src/test/resources/ and cover: a valid load, exact-match lookup
  (a code with different case or surrounding whitespace returns empty), an unknown code
  returning empty, a same-category duplicate being skipped, a different-category duplicate
  failing, an unknown category failing, a missing file failing, and CRLF input.
- Make sure ./mvnw clean verify passes.

## Docker
- The image doesn't contain data/ yet, so the container would now fail fast on startup.
  Copy data/ into the image (resolved against WORKDIR /app) and document in the README
  that DATA_DIR can point to a mounted volume instead.
```

---

## 7. Review suppliers.csv for duplicate and inconsistent supplier data

**Assistant:** Claude Code (Claude Opus 5.5)

```text
lets research suppliers.csv

given the suppliers.csv file in the data directory

review this data for duplications and inconsistencies

paying attention to supplier_id, service_zips, product_categories, customer_satisfaction_score, and can_mail_order?

we'll be loading this data into memory and I need to understand where data needs to be deduped, trimmed, case modified, or parsed so that there is no record collision by supplier_id

service_zips and product_categories are comma separated lists inside a single field, check each value after splitting

are there any duplicate supplier_ids with different data?
are there any product_categories values that don't match the category values in products.csv? (This is enumerated as Category under com.tylermatteson.orderrouting.product)
are there any customer_satisfaction_score or can_mail_order? values that aren't a clean number or y/n? I would want to pull in can_mail_order as a boolean and make sure customer_satisfaction_score was limited to 1-10, or "no ratings yet"

for service_zips(assuming zips are normalized):
- the value can either be a range 00100-99999, a list of ranges "10451-10478, 10479-10502", or a list of individual zips "11357, 11360, 11363, 11371, 11377, 11382, 11388, 11402, 11403, 11405, 11411, 11413, 11415, 11420" -> anything else I missed?
- my inital thought is to pull this in as follows -> Class ZipCoverage which contains both a Set(?) of zips and a List of zip ranges, this could be a class called ZipRange with start and end(with a helper method to determine if a zip is within the range) -> does this cover the data? -> ZipCoverage would need a helper method to check the Set of individual zips and ZipRanges
```

**Response:**

I went through all 1,100 rows of `data/suppliers.csv`. You won't get any record collisions on `supplier_id`, but two fields need real parsing work: `service_zips` has a leading-zero problem, and `customer_satisfaction_score` contains decimals.

**1. supplier_id: no duplicates**
- **No duplicate IDs.** That holds for exact matches, after trimming and uppercasing, and even comparing just the numbers (so `SUP-001` and `SUP-0001` couldn't collide). No full rows are duplicated either.
- **The ID format changes partway through:** `SUP-001`…`SUP-100` use three digits, `SUP-T001`…`SUP-T014` follow, then `SUP-0115`…`SUP-1100` use four. Keep the ID as the exact string. Don't parse the number out of it.
- **`SUP-T001`–`T014` are test rows** ("Sentinel Exclusive Local", "Sentinel Tiebreak A/B", and so on). They use fake zips like `00001`, `00003` and `00098`. They look like routing test fixtures, so keep them, but don't let a "valid zip" check (≥ 00501) reject them.
- 11 supplier names appear twice under different IDs (e.g. "MedEquip Depot Inc"). That's only cosmetic, since names aren't keys.
- The header has a typo, `suplier_name`. If you map columns by header name, use that exact spelling.

**2. product_categories: every value maps after trim and lowercase**
- All 24 `Category` values appear, and nothing is left unmatched once each value is **trimmed and lowercased**.
- The only case problems are `CPAP` (6 rows) and `CPM machine` (3 rows). Since `Category.fromLabel` is strict, normalize before you call it.
- No empty values, and no category repeats within a row. Splitting on `,` and trimming is enough.

**3. customer_satisfaction_score: valid, but not integers**
- 184 rows say `no ratings yet`. Everything else parses as a number from **2.6 to 10.0**, so nothing falls outside 1–10.
- **Most scores are decimals** (`7.8`, `5.5`), and whole numbers come both ways (`9` and `9.0`). An `int` won't work. Use something like `OptionalDouble` or a `Double`/`BigDecimal` that can be empty, with "no ratings yet" stored as empty.
- I'd make an out-of-range score fail the load rather than clamping it to 1–10. No current row would trip that, and clamping would hide bad data.

**4. can_mail_order?: clean**
- Only `y` (459) and `n` (641) appear. A strict y/n → boolean conversion that throws on anything else is safe.

**5. service_zips: the shapes you missed**

Your three shapes are there, plus these:
1. **Ranges and single zips in the same field** (157 rows), e.g. `"60684-60719, 60615, 90020, 60691, 2242, 2247"`.
2. **A single zip alone** (13 rows), e.g. `00001`.
3. **4-digit zips: the leading zero was dropped.** 500 rows contain values like `2110` or `2164-2213`, which are Massachusetts zips 02110 and 02164–02213. So the zips are **not** normalized. 395 of those rows mix 4- and 5-digit values. They need left-padding to 5 digits, or parsing as `int`.
4. **Overlapping ranges, and zips that fall inside a range in the same row.** Examples: `SUP-016` has `"2164-2213, 2143-2193, 2154-2171"`, and `SUP-0218` lists `60691` inside `60684-60719`. A contains-check isn't affected, so you don't need to merge them.
5. **`00100-99999`** (60 rows) always appears alone, and 29 of those suppliers have `can_mail_order? = n`. Full zip coverage and mail order are separate things, so don't treat one as implying the other.

Things I checked that came back clean: no backwards ranges (start > end), no repeated zips within a row, and separators are always `", "`. The file uses CRLF line endings, so make sure your CSV reader strips `\r`, or the last column will come through as `y\r`.

**6. Your ZipCoverage design**

`ZipCoverage { Set<zip> zips; List<ZipRange> ranges; boolean covers(zip) }` with `ZipRange.contains` covers everything in the file. Some suggestions:
- **Store zips as `int`.** That fixes the 4-digit problem for free, because `2110` and `02110` both become 2110, and range checks become plain numeric comparisons. If you'd rather keep strings, left-pad to 5 digits first. Fixed-width strings then compare correctly.
- **Normalize the order's zip the same way** before calling `covers`. Orders may have the same dropped-zero problem, or use ZIP+4.
- Have the `ZipRange` constructor check `start <= end`.
- One small optional speedup: a full-span range can return `true` straight away. Linear scans are fine at this size.

---

## 8. Plan the supplier CSV loading and repository prompt

**Assistant:** Claude Code (Claude Opus 5.5) — /plan mode

```text
given that research help me build out a clean prompt for a fresh session
```

Clarifying answers given during planning:
- SupplierRepository lookups: findById and findByCategory for now
- customer_satisfaction_score: OptionalDouble
- Duplicate supplier_id: always fail

**Response:**

Here's a prompt you can paste into a fresh session. It follows the existing product loading pattern and includes your answers on repository lookups, score type and duplicate handling.

````text
On the data-loading branch, add supplier data loading and a supplier repository layer.
Follow the existing product loading pattern in com.tylermatteson.orderrouting.product
(ProductCsvLoader, ProductConfiguration, InMemoryProductRepository, ProductDataException)
and reuse config/AppProperties (app.data-dir / DATA_DIR) and the Category enum.

## Context
- Java 25, Spring Boot 4.1.2, Maven, Apache Commons CSV 1.14.1 (already in pom.xml).
- data/suppliers.csv header (note the typo, use it exactly):
  supplier_id,suplier_name,service_zips,product_categories,customer_satisfaction_score,can_mail_order?
- 1,100 rows, CRLF line endings, no duplicate supplier_ids today.
- service_zips and product_categories are comma-separated lists inside one quoted field.
- 4-digit zips appear (500 rows): leading zero dropped, e.g. "2110" = 02110, "2164-2213" = 02164-02213.
- Rows SUP-T001..SUP-T014 are routing test fixtures with fake zips (00001, 00003, 00098...). Keep them.

## Loading (at startup)
- Read <dataDir>/suppliers.csv. Fail fast (context fails to start, clear message) if the file is
  missing/unreadable, a required header column is missing, or a row has the wrong column count.
- Build in a SupplierCsvLoader that returns an immutable Map<String, Supplier> keyed by supplier_id.
- Add a SupplierDataException (mirroring ProductDataException). Every error names the file,
  line number, supplier_id, and the offending raw value.

## Field rules
- supplier_id: exact string, no trim or case change (same as product_code). Blank fails.
  Any duplicate supplier_id fails loading, naming both line numbers.
- suplier_name: keep as-is (map to Supplier.name).
- product_categories: split on ",", trim, toLowerCase(Locale.ROOT), map with Category.fromLabel.
  Unknown or empty value fails. Store as an immutable EnumSet-backed Set<Category>.
  An empty list fails.
- customer_satisfaction_score: trim; "no ratings yet" (case-insensitive) -> OptionalDouble.empty().
  Otherwise parse as double ("9" and "9.0" both valid); not a number, NaN, or outside [1, 10] fails.
  Do not clamp.
- can_mail_order?: trim, case-insensitive "y" -> true, "n" -> false; anything else fails.
- service_zips -> ZipCoverage:
  - Split on ",", trim each token. A token is either a single zip (1-5 digits) or a range
    "start-end" (each side 1-5 digits). Anything else, an empty token, or an empty field fails.
  - Parse zips as int so leading zeros don't matter ("2110" == "02110" == 2110).
  - ZipRange record(int start, int end): constructor rejects start > end and values outside
    0..99999; contains(int zip).
  - ZipCoverage: immutable Set<Integer> of single zips + List<ZipRange>; covers(int zip) checks both.
    Overlapping ranges and singles inside ranges exist in the data; allow them, no merging needed.
  - Also provide a static ZipCoverage/zip helper that normalizes an incoming zip string the same
    way (trim, digits only, 1-5 digits -> int; also accept ZIP+4 "02110-1234" by taking the first
    part) so order zips can be compared later. Invalid input -> empty OptionalInt, not an exception.
  - Full coverage "00100-99999" is just a range; it is NOT the same as can_mail_order.

## Model and repository (https://java-design-patterns.com/patterns/repository/)
- Supplier record: id, name, ZipCoverage serviceZips, Set<Category> categories,
  OptionalDouble satisfactionScore, boolean canMailOrder. Defensive immutable copies.
- SupplierRepository interface:
  - Optional<Supplier> findById(String id) — exact match; unknown id returns empty.
  - List<Supplier> findByCategory(Category category) — suppliers carrying that category, in file
    order; empty list (never null) when none; unmodifiable. Precompute a Map<Category, List<Supplier>>.
- InMemorySupplierRepository backed by the loaded map; parsing stays in the loader.
- SupplierConfiguration (like ProductConfiguration) loads suppliers.csv at startup. Put everything
  in a new package com.tylermatteson.orderrouting.supplier.

## Tests
- Fixture CSVs under src/test/resources/suppliers/. Cover: valid load; CRLF input; exact-match
  findById (different case / surrounding whitespace returns empty); unknown id; findByCategory hits,
  miss (empty list), and file order; mixed-case category normalized; unknown category fails;
  duplicate supplier_id fails; score "no ratings yet", "9", "9.0", "7.8" pass, "0.9", "10.1", "abc"
  fail; mail-order y/n pass, other values fail; zips: single, list, range, list of ranges,
  mixed ranges + singles, 4-digit zip and 4-digit range, inverted range fails, malformed token fails,
  covers() true/false at range boundaries; order-zip normalization incl. ZIP+4; missing file and
  missing column fail context startup.
- Extend the full-boot test (like ProductDataLoadingTests) to load the real data/suppliers.csv:
  assert 1,100 suppliers, SUP-T001 present, SUP-016 covers 2170 and 02170.
- ./mvnw clean verify must pass.

## Docs
- README: document supplier loading rules next to the product rules (data/ is already copied
  into the Docker image, so no Dockerfile change should be needed — verify).

Don't commit. Summarize the changes when you're done.
````

---

## 9. Supplier CSV loading and repository layer

**Assistant:** Claude Code (Claude Opus 5.5)

The prompt generated in step 8, run in a fresh session, with a "Shared Code" section added at the end.

```text
On the data-loading branch, add supplier data loading and a supplier repository layer.
Follow the existing product loading pattern in com.tylermatteson.orderrouting.product
(ProductCsvLoader, ProductConfiguration, InMemoryProductRepository, ProductDataException)
and reuse config/AppProperties (app.data-dir / DATA_DIR) and the Category enum.

## Context
- Java 25, Spring Boot 4.1.2, Maven, Apache Commons CSV 1.14.1 (already in pom.xml).
- data/suppliers.csv header (note the typo, use it exactly):
  supplier_id,suplier_name,service_zips,product_categories,customer_satisfaction_score,can_mail_order?
- 1,100 rows, CRLF line endings, no duplicate supplier_ids today.
- service_zips and product_categories are comma-separated lists inside one quoted field.
- 4-digit zips appear (500 rows): leading zero dropped, e.g. "2110" = 02110, "2164-2213" = 02164-02213.
- Rows SUP-T001..SUP-T014 are routing test fixtures with fake zips (00001, 00003, 00098...). Keep them.

## Loading (at startup)
- Read <dataDir>/suppliers.csv. Fail fast (context fails to start, clear message) if the file is
  missing/unreadable, a required header column is missing, or a row has the wrong column count.
- Build in a SupplierCsvLoader that returns an immutable Map<String, Supplier> keyed by supplier_id.
- Add a SupplierDataException (mirroring ProductDataException). Every error names the file,
  line number, supplier_id, and the offending raw value.

## Field rules
- supplier_id: exact string, no trim or case change (same as product_code). Blank fails.
  Any duplicate supplier_id fails loading, naming both line numbers.
- suplier_name: keep as-is (map to Supplier.name).
- product_categories: split on ",", trim, toLowerCase(Locale.ROOT), map with Category.fromLabel.
  Unknown or empty value fails. Store as an immutable EnumSet-backed Set<Category>.
  An empty list fails.
- customer_satisfaction_score: trim; "no ratings yet" (case-insensitive) -> OptionalDouble.empty().
  Otherwise parse as double ("9" and "9.0" both valid); not a number, NaN, or outside [1, 10] fails.
  Do not clamp.
- can_mail_order?: trim, case-insensitive "y" -> true, "n" -> false; anything else fails.
- service_zips -> ZipCoverage:
  - Split on ",", trim each token. A token is either a single zip (1-5 digits) or a range
    "start-end" (each side 1-5 digits). Anything else, an empty token, or an empty field fails.
  - Parse zips as int so leading zeros don't matter ("2110" == "02110" == 2110).
  - ZipRange record(int start, int end): constructor rejects start > end and values outside
    0..99999; contains(int zip).
  - ZipCoverage: immutable Set<Integer> of single zips + List<ZipRange>; covers(int zip) checks both.
    Overlapping ranges and singles inside ranges exist in the data; allow them, no merging needed.
  - Also provide a static ZipCoverage/zip helper that normalizes an incoming zip string the same
    way (trim, digits only, 1-5 digits -> int; also accept ZIP+4 "02110-1234" by taking the first
    part) so order zips can be compared later. Invalid input -> empty OptionalInt, not an exception.
  - Full coverage "00100-99999" is just a range; it is NOT the same as can_mail_order.

## Model and repository (https://java-design-patterns.com/patterns/repository/)
- Supplier record: id, name, ZipCoverage serviceZips, Set<Category> categories,
  OptionalDouble satisfactionScore, boolean canMailOrder. Defensive immutable copies.
- SupplierRepository interface:
  - Optional<Supplier> findById(String id) — exact match; unknown id returns empty.
  - List<Supplier> findByCategory(Category category) — suppliers carrying that category, in file
    order; empty list (never null) when none; unmodifiable. Precompute a Map<Category, List<Supplier>>.
- InMemorySupplierRepository backed by the loaded map; parsing stays in the loader.
- SupplierConfiguration (like ProductConfiguration) loads suppliers.csv at startup. Put everything
  in a new package com.tylermatteson.orderrouting.supplier.

## Tests
- Fixture CSVs under src/test/resources/suppliers/. Cover: valid load; CRLF input; exact-match
  findById (different case / surrounding whitespace returns empty); unknown id; findByCategory hits,
  miss (empty list), and file order; mixed-case category normalized; unknown category fails;
  duplicate supplier_id fails; score "no ratings yet", "9", "9.0", "7.8" pass, "0.9", "10.1", "abc"
  fail; mail-order y/n pass, other values fail; zips: single, list, range, list of ranges,
  mixed ranges + singles, 4-digit zip and 4-digit range, inverted range fails, malformed token fails,
  covers() true/false at range boundaries; order-zip normalization incl. ZIP+4; missing file and
  missing column fail context startup.
- Extend the full-boot test (like ProductDataLoadingTests) to load the real data/suppliers.csv:
  assert 1,100 suppliers, SUP-T001 present, SUP-016 covers 2170 and 02170.
- ./mvnw clean verify must pass.

## Docs
- README: document supplier loading rules next to the product rules (data/ is already copied
  into the Docker image, so no Dockerfile change should be needed — verify).

## Shared Code - Do this first
Refactor product loading to use these, and keep the existing product tests passing.
- A CsvFileReader in com.tylermatteson.orderrouting.csv that does the generic work from ProductCsvLoader: opening and checking the file, checking columns, and line numbers. It hands each row to the loader. Each loader keeps its own field rules and duplicate handling.
- Category.parse(String raw), which trims and lowercases, then calls fromLabel. fromLabel stays exact.
- A DataLoadException base class that ProductDataException and SupplierDataException both extend.

Don't commit. Summarize the changes when you're done.
```

**Note on code reuse:** The plan from step 8 had the supplier loader mirror `ProductCsvLoader`, which would have copied its file, header and column-count checks and its line-number handling into a second class. Before running it, the "Shared Code" section was added so that this generic work moves into one `CsvFileReader` first, and product loading is refactored onto it before any supplier code is written.

- **What is shared:** `CsvFileReader` checks the file, the required columns and the column count, and it tracks line numbers. `DataLoadException` is the common base class for both exceptions. `Category.parse` holds the trim-and-lowercase step that both loaders need.
- **What stays in each loader:** the field rules and duplicate handling, because the two data sets genuinely differ. Products skip a same-category duplicate with a WARN, while suppliers fail on any duplicate.
- **What is kept intact:** each data set still throws its own exception type, `Category.fromLabel` stays exact, and the existing product tests passed unchanged after the refactor. The refactor also gives both files the same error format: file, line and row id.

---

## 10. Plan the order routing controller shell and acceptance tests prompt

**Assistant:** Claude Code (Claude Opus 5.5)

```text
Help me finish building out this prompt including the cases using data within the /data dir, this is my rough outline:

On the following branch order-routing:
- Following a TDD(Test Driven Development) strategy
- Do not build out routing logic yet, leave a placeholder TODO comment in its place
- Given data refernced in /data dir of this project including sample_orders.json 
- Build the shell for the POST /api/route controller and success/response objects includeing error string constants
    - Known error string constants: 
- Build out acceptance test cases only in the form of expanding the order-routing-service.postman_collection.json test suite and adding JUnits(@SpringBootTest with MockMvc)
- Test for additional layers neccsary for the routing layer will be added when the logic is implemented

The routing logic is as follows: 
1. Feasibility: route an item only to a supplier that carries its category and can deliver to the customer. That means the supplier serves the customer’s ZIP, or the order allows mail order and the supplier ships by mail.
2. Customer experience: use as few suppliers (shipments) as possible. If one supplier can fulfill every item, send the whole order to it, even when two or more suppliers together would have higher ratings.
3. Quality: among plans with the same number of shipments, prefer the one with higher-rated suppliers. Unrated suppliers rank below rated ones.
4. Geographic preference: when plans’ ratings are within a set tolerance of each other, prefer the plan with more local suppliers over mail order.

The API Controller details: 
- POST /api/route controller 
- Example Request payload: 
    {
    "order_id": "ORD-EXAMPLE",
    "customer_zip": "10015",
    "mail_order": false,
    "items": [
    { "product_code": "WC-STD-001", "quantity": 1 },
    { "product_code": "OX-PORT-024", "quantity": 1 }
    ]
- Example success response: 
    {
    "feasible": true,
    "routing": [
    {
    "supplier_id": "SUP-005",
    "supplier_name": "Respiratory Care Co Co",
    "items": [
    {
    "product_code": "WC-STD-001",
    "quantity": 1,
    "category": "wheelchair",
    "fulfillment_mode": "local"
    }
    ]
    }
    ]
    }
- Example failure response: 
    {
    "feasible": false,
    "errors": [
    "Order must include at least one line item.",
    "Order must include a valid customer_zip."
    ]
    }
```

**Response:**

````text
On a new order-routing branch (from main), build the shell for POST /api/route and its acceptance
tests, test first (TDD). Do NOT implement routing logic: leave a placeholder with a
`// TODO: routing logic` comment. Tests for the routing layer's internal classes will be added when
the logic is built. In this step, the only tests are acceptance tests at the HTTP level.

## Context
- Java 25, Spring Boot 4.1.2, Spring Web MVC, Jackson. Base package com.tylermatteson.orderrouting.
- Reference data is already loaded at startup from data/ (see README "Reference data"):
  ProductRepository (findByCode) and SupplierRepository (findById, findByCategory), Category,
  ZipCoverage (including the order-zip normalizer that returns OptionalInt).
- data/sample_orders.json has 3 orders. They include extra fields (`priority`, `notes`) that the API
  must ignore.

## Routing rules (for the TODO comment and the test expectations, not to implement yet)
1. Feasibility: route an item only to a supplier that carries its category and can deliver to the
   customer. That means the supplier serves the customer's ZIP (fulfillment_mode "local"), or the
   order allows mail order and the supplier ships by mail (fulfillment_mode "mail"). A supplier
   that serves the ZIP is "local" even if it can also mail.
2. Customer experience: use as few suppliers (shipments) as possible. If one supplier can fulfill
   every item, send the whole order to it, even when two or more suppliers together would have
   higher ratings.
3. Quality: among plans with the same number of shipments, prefer higher-rated suppliers. Unrated
   suppliers rank below rated ones.
4. Geographic preference: when plans' ratings are within a set tolerance of each other, prefer the
   plan with more local suppliers over mail order. Tolerance comes from
   `app.routing.rating-tolerance` (default 0.5). Add the property now.
5. Final tie-break, so results are deterministic: lowest supplier_id (string order).
- If any item can't be fulfilled, the order is infeasible: return no routing, and one error per
  infeasible item.

## API contract
POST /api/route, Content-Type application/json. All JSON fields are snake_case. Use @JsonNaming or
@JsonProperty on these DTOs only, not a global naming strategy. Unknown request fields are ignored.

Request:
{ "order_id": "ORD-EXAMPLE", "customer_zip": "10015", "mail_order": false,
  "items": [ { "product_code": "WC-STD-001", "quantity": 1 } ] }

Success (200):
{ "feasible": true,
  "routing": [ { "supplier_id": "SUP-0636", "supplier_name": "Care Supply Corp #636",
                 "items": [ { "product_code": "WC-STD-001", "quantity": 1,
                              "category": "wheelchair", "fulfillment_mode": "local" } ] } ] }

Failure:
{ "feasible": false, "errors": [ "Order must include at least one line item.",
                                 "Order must include a valid customer_zip." ] }

- Leave out `routing` when not feasible, and leave out `errors` when feasible.
- `category` is the Category label (lower-case, e.g. "cpap"). `fulfillment_mode` is "local" or
  "mail" (an enum serialized lower-case).
- Shipment order is the order in which each supplier's first item appears in the request. Items
  within a shipment keep request order.
- product_code is trimmed and upper-cased before lookup (README "Product and supplier codes"). The
  response echoes the normalized code.
- customer_zip is valid when the existing ZipCoverage normalizer accepts it (1-5 digits, or ZIP+4).
- Status codes: 400 for request validation errors (including malformed JSON and unknown
  product_code). 200 with feasible=false when the request is valid but no route exists.
- Collect all validation errors, not just the first. Order them: order_id, items, customer_zip,
  mail_order, then line-item errors in request order.

## Error string constants
Put them in one final class (e.g. route/RouteErrors). Use String.format templates for the
parameterized ones. Line numbers are 1-based. Tests must assert against these constants, not
copied strings.
- NO_LINE_ITEMS        = "Order must include at least one line item."     (items missing or [])
- INVALID_CUSTOMER_ZIP = "Order must include a valid customer_zip."        (missing, blank, invalid)
- MISSING_ORDER_ID     = "Order must include an order_id."                  (missing or blank)
- MISSING_MAIL_ORDER   = "Order must include mail_order (true or false)."   (missing or null)
- MISSING_PRODUCT_CODE = "Line item %d must include a product_code."
- INVALID_QUANTITY     = "Line item %d must have a quantity of at least 1." (missing, 0, negative)
- UNKNOWN_PRODUCT_CODE = "Unknown product_code: %s."
- MALFORMED_REQUEST    = "Request body must be a valid JSON order."         (unparseable or wrong types)
- NO_FEASIBLE_SUPPLIER = "No supplier can fulfill product_code %s (%s) for customer_zip %s."
                         (code, category label, zip as given)

## Shell to build (package com.tylermatteson.orderrouting.route)
- RouteController: POST /api/route. It delegates to a RouteRequestValidator, then to a
  RoutingService.
- Request/response records: RouteRequest (with a nested LineItem) and RouteResponse (with nested
  Shipment, RoutedItem and FulfillmentMode). Use factory methods RouteResponse.success(...) and
  RouteResponse.failure(...).
- RouteRequestValidator: implement it now. It applies the validation rules and normalization
  above, using ProductRepository for unknown codes. It returns a ValidatedOrder, or throws a
  nested ValidationException carrying every error.
- RoutingService interface, plus a placeholder implementation whose route(...) method holds the
  `// TODO: routing logic` comment (rules 1-5 above summarized in it) and throws
  UnsupportedOperationException.
- An @RestControllerAdvice that maps malformed JSON to 400 MALFORMED_REQUEST, and a
  ValidationException to 400 with its errors.

## Acceptance tests (write these first)
JUnit: RouteControllerAcceptanceTests, @SpringBootTest + @AutoConfigureMockMvc (same style as
HealthEndpointTests), against the real data/. Use @Nested groups.
Mark the "Routing" and "Sample orders" groups @Disabled("routing logic not implemented"). Validation
must pass now, so ./mvnw clean verify stays green.

### Validation (passing now): each returns 400, feasible=false, no `routing` key
- items is [] -> [NO_LINE_ITEMS]. items missing -> [NO_LINE_ITEMS].
- customer_zip missing, "", "ABCDE", "123456" -> [INVALID_CUSTOMER_ZIP] (each case).
- items [] and customer_zip missing -> [NO_LINE_ITEMS, INVALID_CUSTOMER_ZIP], in that order.
- order_id missing or "  " -> [MISSING_ORDER_ID].
- mail_order missing -> [MISSING_MAIL_ORDER].
- Line 2 product_code blank -> ["Line item 2 must include a product_code."].
- quantity 0, -1, and missing -> INVALID_QUANTITY for that line.
- product_code "XX-NOPE-999" -> ["Unknown product_code: XX-NOPE-999."].
- Malformed body ("{not json") and quantity "abc" -> [MALFORMED_REQUEST].
- An order with every error at once returns all of them, in the documented order.

### Routing (@Disabled until the logic lands). Fixtures use mail_order false unless noted
| # | Rule shown | zip / mail | items | Expected |
|---|---|---|---|---|
| R1 | Feasibility: no mail without permission | 00001 / false | WC-STD-001 | SUP-T001, local (not SUP-T002, which doesn't serve 00001) |
| R2 | A mail-capable supplier serving the zip is local | 00002 / false | WC-STD-001 | SUP-T002, local |
| R3 | Deterministic tie-break | 00003 / false | CN-STD-063 | SUP-T003 (ties T004 at 8.0); same result on repeat |
| R4 | Local beats mail at equal rating | 00005 / true | WK-STD-009 | SUP-T005, local (T006 and real suppliers are 10.0 by mail only) |
| R5 | Consolidation beats ratings | 00007 / false | WC-STD-001, CN-STD-063, WK-STD-009 | one shipment, SUP-T007 (6.0), 3 items in request order, despite the 10.0 specialists T008-T010 |
| R6 | Quality on a single item | 00007 / false | WC-STD-001 | SUP-T008 (10.0 over T007 6.0) |
| R7 | Unrated suppliers are still feasible | 00040 / false | RL-STD-058 | SUP-T011 (both unrated, tie-break) |
| R8 | Rated beats unrated | 00040 / true | RL-STD-058 | supplier is not T011 or T012, fulfillment_mode "mail" |
| R9 | Infeasible: no coverage | 00099 / false | WC-STD-001 | 200, feasible=false, [NO_FEASIBLE_SUPPLIER(WC-STD-001, wheelchair, 00099)] |
| R10 | Only option, low rating | 00098 / false | WC-STD-001 | SUP-T014 |
| R11 | Full-range coverage isn't mail | 00050 / false | CM-SENT-001 | infeasible (SUP-T013 covers 00100-99999 only) |
| R12 | One bad item makes the order infeasible | 00001 / false | WC-STD-001, CN-STD-063 | feasible=false, exactly one error, for CN-STD-063 (cane); no routing |
| R13 | Normalization | 00001-1234 / false | " wc-std-001 " qty 3 | SUP-T001; item echoes "WC-STD-001", quantity 3, category "wheelchair" |

### Sample orders (@Disabled): post each order from data/sample_orders.json as-is (priority and notes included)
- ORD-001 (10015, no mail) -> one shipment SUP-0636 "Care Supply Corp #636", both items local.
  Only 4 suppliers cover both items locally; SUP-0636 has the highest rating, 6.8.
- ORD-002 (77059, no mail) -> one shipment SUP-0928 "Home Solutions Co #928", all 4 items local
  (it beats SUP-0460 at 5.3).
- ORD-003 (02130, mail allowed) -> one shipment SUP-023 "Community Care Equipment", all 3 items by
  mail, CP-MSK-FF-035 quantity 2. SUP-023 and SUP-065 are both 10.0 by mail, and tie-break picks
  SUP-023. The best local single supplier, SUP-0960 at 7.0, is outside the tolerance.

### Postman
Add a "Route" folder to postman/order-routing-service.postman_collection.json. It needs one request
per validation case above (asserting status, feasible=false and the exact errors array), plus one
request per routing case and per sample order. In the routing and sample-order requests, put this
first line in each test script:
`// Pending: routing logic not implemented. Expected to fail until then.`
Keep the collection-level response-time test. Use {{baseUrl}}.

## Docs
- README: fill in "Routing criteria" from the rules above, and add an "API" section with the request
  and response shape, status codes and error list.

Don't commit. Summarize the changes when you're done, and list any routing-test expectation that you
think contradicts the rules.
````

---

## 11. Plan the routing logic implementation prompt

**Assistant:** Claude Code (Claude Opus 5.5) — /plan mode

```text
Plan the routing logic implementation, using a 2-part strategy.
README "Routing rules" and the TODO in PlaceholderRoutingService are the spec.

1. SupplierEligibility (rule 1): check category first, then local, then mail order.
   Local wins whenever the supplier serves the ZIP. Returns Optional FulfillmentMode.
2. MinimumShipmentRouter (rules 2-5), in priority order: find the plans with the fewest
   shipments, then keep those whose ratings are within app.routing.rating-tolerance of
   the best, then prefer the most local suppliers. Break any remaining tie by rating, then
   supplier_id. Fewer shipments always wins, because plans with more suppliers never reach
   selection. Local preference can outweigh a small rating difference, never a large one.
   - Plan rating: the mean of supplier scores. A plan with any unrated supplier ranks below
     all fully rated plans (fewer unrated wins). The tolerance only compares plans within the same group.
   - An item that more than one plan supplier carries goes to the best one by (local, rating, id).
3. OrderRoutingService replaces PlaceholderRoutingService and ties 1 and 2 together.
   Any line with no eligible supplier → RouteResponse.failure, one
   RouteErrors.noFeasibleSupplier per line. Shipments are ordered by each supplier's first item in the request.

Give me the plan and the implementation prompt to use in a fresh session.
```

**Response:**

````text
Implement the order routing logic in order-routing-service (branch order-routing). The spec is README
"Routing criteria" plus the TODO in src/main/java/com/tylermatteson/orderrouting/route/PlaceholderRoutingService.java.
The target tests already exist but are @Disabled: RouteControllerAcceptanceTests.Routing (r1-r13) and .SampleOrders.
Note: the route/ package and its tests are untracked/uncommitted work in progress; don't discard them.

Build three classes in com.tylermatteson.orderrouting.route:

1. SupplierEligibility (rule 1): final class, static
   Optional<FulfillmentMode> modeFor(Supplier, Category, int zip, boolean mailOrder).
   Check category first, then local (supplier.serviceZips().covers(zip) -> LOCAL, which wins even if the supplier
   can mail), then mail order (mailOrder && canMailOrder -> MAIL), else empty.

2. MinimumShipmentRouter (rules 2-5): plain class (not a bean), constructor takes double ratingTolerance.
   Input: the order lines plus, per line, a non-empty List<Candidate(Supplier, FulfillmentMode)>.
   Output: List<RouteResponse.Shipment>.
   - Enumerate minimum covers with iterative-deepening DFS (k=1,2,...; branch on the lowest-index uncovered line's
     candidates; dedupe plans by sorted supplier ids; stop at the first k with any cover). Plans with more
     suppliers never reach selection, so fewer shipments always wins.
   - Select in priority order:
     a) fewest unrated suppliers (any unrated ranks below all fully rated plans; the tolerance only compares
        plans within the same group);
     b) plan rating = mean of the rated suppliers' scores (unweighted); keep plans with
        rating >= best - tolerance - 1e-9 (if no supplier is rated, all plans in the group tie);
     c) most LOCAL suppliers;
     d) highest rating, then lexicographically smallest sorted supplier-id list.
     Local preference can outweigh a small rating difference, never a large one.
   - Within the chosen plan, each line goes to the eligible plan supplier best by (LOCAL first, rated before
     unrated, higher score, lower id); fulfillment_mode is that supplier's mode for the line.
   - Shipments ordered by each supplier's first assigned line in the request; items keep request order.
     Use RoutedItem's Category constructor overload.

3. OrderRoutingService (@Service implements RoutingService): replaces PlaceholderRoutingService (delete it).
   Constructor (SupplierRepository, AppProperties). Candidates per line come from findByCategory (cache per
   category) through SupplierEligibility. If any line has no candidates, return RouteResponse.failure with one
   RouteErrors.noFeasibleSupplier(code, category.label(), order.customerZip()) per infeasible line in request order.
   Otherwise return RouteResponse.success(router.route(...)).

Tests (TDD: write the unit tests first, then implement):
- SupplierEligibilityTests: wrong category; local; local+mail-capable is LOCAL; mail allowed/disallowed; neither.
- MinimumShipmentRouterTests (hand-built Suppliers, no Spring): one generalist beats higher-rated specialists;
  local wins within tolerance; mail wins outside tolerance; exact-tolerance boundary; tolerance 0; fully rated
  plan beats a higher-rated plan containing an unrated supplier; fewer unrated wins; all-unrated ties go to local
  then id; rating breaks a tie after the local count; id tie-break; item shared by two plan suppliers goes to the
  best by (local, rating, id); shipment ordering by first item.
- RouteControllerAcceptanceTests: remove both @Disabled; add a test with two infeasible lines -> two errors
  in request order.

Docs: update README "Routing criteria" (remove the not-implemented note; document mean rating, unrated grouping,
tolerance-then-local order, per-item assignment). Check the Postman collection for routing requests that assert
the placeholder 500 and update them to the real expectations.

Verify: ./mvnw test passes; then run the app and newman against
postman/order-routing-service.postman_collection.json with postman/local.postman_environment.json.
Don't commit until I review.
````

---

## 12. Order routing logic

**Assistant:** Claude Code (Claude Opus 5.5)

The prompt generated in step 11, run in a fresh session.

```text
Implement the order routing logic in order-routing-service (branch order-routing). The spec is README
"Routing criteria" plus the TODO in src/main/java/com/tylermatteson/orderrouting/route/PlaceholderRoutingService.java.
The target tests already exist but are @Disabled: RouteControllerAcceptanceTests.Routing (r1-r13) and .SampleOrders.
Note: the route/ package and its tests are untracked/uncommitted work in progress; don't discard them.

Build three classes in com.tylermatteson.orderrouting.route:

1. SupplierEligibility (rule 1): final class, static
   Optional<FulfillmentMode> modeFor(Supplier, Category, int zip, boolean mailOrder).
   Check category first, then local (supplier.serviceZips().covers(zip) -> LOCAL, which wins even if the supplier
   can mail), then mail order (mailOrder && canMailOrder -> MAIL), else empty.

2. MinimumShipmentRouter (rules 2-5): plain class (not a bean), constructor takes double ratingTolerance.
   Input: the order lines plus, per line, a non-empty List<Candidate(Supplier, FulfillmentMode)>.
   Output: List<RouteResponse.Shipment>.
   - Enumerate minimum covers with iterative-deepening DFS (k=1,2,...; branch on the lowest-index uncovered line's
     candidates; dedupe plans by sorted supplier ids; stop at the first k with any cover). Plans with more
     suppliers never reach selection, so fewer shipments always wins.
   - Select in priority order:
     a) fewest unrated suppliers (any unrated ranks below all fully rated plans; the tolerance only compares
        plans within the same group);
     b) plan rating = mean of the rated suppliers' scores (unweighted); keep plans with
        rating >= best - tolerance - 1e-9 (if no supplier is rated, all plans in the group tie);
     c) most LOCAL suppliers;
     d) highest rating, then lexicographically smallest sorted supplier-id list.
     Local preference can outweigh a small rating difference, never a large one.
   - Within the chosen plan, each line goes to the eligible plan supplier best by (LOCAL first, rated before
     unrated, higher score, lower id); fulfillment_mode is that supplier's mode for the line.
   - Shipments ordered by each supplier's first assigned line in the request; items keep request order.
     Use RoutedItem's Category constructor overload.

3. OrderRoutingService (@Service implements RoutingService): replaces PlaceholderRoutingService (delete it).
   Constructor (SupplierRepository, AppProperties). Candidates per line come from findByCategory (cache per
   category) through SupplierEligibility. If any line has no candidates, return RouteResponse.failure with one
   RouteErrors.noFeasibleSupplier(code, category.label(), order.customerZip()) per infeasible line in request order.
   Otherwise return RouteResponse.success(router.route(...)).

Tests (TDD: write the unit tests first, then implement):
- SupplierEligibilityTests: wrong category; local; local+mail-capable is LOCAL; mail allowed/disallowed; neither.
- MinimumShipmentRouterTests (hand-built Suppliers, no Spring): one generalist beats higher-rated specialists;
  local wins within tolerance; mail wins outside tolerance; exact-tolerance boundary; tolerance 0; fully rated
  plan beats a higher-rated plan containing an unrated supplier; fewer unrated wins; all-unrated ties go to local
  then id; rating breaks a tie after the local count; id tie-break; item shared by two plan suppliers goes to the
  best by (local, rating, id); shipment ordering by first item.
- RouteControllerAcceptanceTests: remove both @Disabled; add a test with two infeasible lines -> two errors
  in request order.

Docs: update README "Routing criteria" (remove the not-implemented note; document mean rating, unrated grouping,
tolerance-then-local order, per-item assignment). Check the Postman collection for routing requests that assert
the placeholder 500 and update them to the real expectations.

Verify: ./mvnw test passes; then run the app and newman against
postman/order-routing-service.postman_collection.json with postman/local.postman_environment.json.
Don't commit until I review.
```

---

## 13. Always return HTTP 200 from POST /api/route

**Assistant:** Claude Code (Claude Opus 5.5)

```text
the spec says POST /api/route always returns http 200. fix that.

- RouteExceptionHandler: malformed json, wrong field types and validation errors
  return 200 with feasible=false and the same errors (remove @ResponseStatus BAD_REQUEST)
- unsupported content-type also returns 200 with feasible=false and a clear error constant
- don't add a catch-all for unexpected exceptions. server bugs stay 500
- update the acceptance tests to expect 200 everywhere, including the
  everyErrorAtOnce case
- update postman/order-routing-service.postman_collection.json: every request
  expects status 200 (18 currently expect 400). keep the feasible=false and errors assertions
- update the controller javadoc and the README status table: always 200,
  feasible tells you the outcome
```

---

## 14. Rename fulfillment_mode "mail" to "mail_order"

**Assistant:** Claude Code (Claude Opus 5.5)

```text
rename fulfillment_mode "mail" to "mail_order" (enum MAIL -> MAIL_ORDER).
update the tests, postman assertions and README to match. run ./mvnw test.
```

---

## 15. Remove SupplierRepository.findById

**Assistant:** Claude Code (Claude Opus 5.5)

```text
remove SupplierRepository.findById and its implementation; it's only used by tests.
update those tests to go through findByCategory or delete them if redundant.
run ./mvnw test.
```

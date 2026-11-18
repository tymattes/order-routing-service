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

---

## 3. Review products.csv for duplicate and inconsistent product codes

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

## 4. Plan the product CSV loading and repository prompt

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

## 5. Product CSV loading and repository layer

**Assistant:** Claude Code (Claude Opus 5.5)

The prompt generated in step 4, run in a fresh session (without the trailing "Don't commit" line).

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

## 6. Review suppliers.csv for duplicate and inconsistent supplier data

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

## 7. Plan the supplier CSV loading and repository prompt

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

## 8. Supplier CSV loading and repository layer

**Assistant:** Claude Code (Claude Opus 5.5)

The prompt generated in step 7, run in a fresh session, with a "Shared Code" section added at the end.

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

**Note on code reuse:** The plan from step 7 had the supplier loader mirror `ProductCsvLoader`, which would have copied its file, header and column-count checks and its line-number handling into a second class. Before running it, the "Shared Code" section was added so that this generic work moves into one `CsvFileReader` first, and product loading is refactored onto it before any supplier code is written.

- **What is shared:** `CsvFileReader` checks the file, the required columns and the column count, and it tracks line numbers. `DataLoadException` is the common base class for both exceptions. `Category.parse` holds the trim-and-lowercase step that both loaders need.
- **What stays in each loader:** the field rules and duplicate handling, because the two data sets genuinely differ. Products skip a same-category duplicate with a WARN, while suppliers fail on any duplicate.
- **What is kept intact:** each data set still throws its own exception type, `Category.fromLabel` stays exact, and the existing product tests passed unchanged after the refactor. The refactor also gives both files the same error format: file, line and row id.

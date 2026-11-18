package com.tylermatteson.orderrouting.product;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tylermatteson.orderrouting.csv.CsvFileReader;

/**
 * Parses a products CSV file (header {@code product_code,product_name,category}) into an immutable map keyed by
 * product code.
 *
 * <p>Rules:
 * <ul>
 * <li>{@code product_code} is used exactly as written: no trimming, no case change. A blank code fails loading.</li>
 * <li>{@code category} is normalized with {@link Category#parse} and must match a {@link Category} label.</li>
 * <li>A repeated code with the same normalized category is skipped with a WARN; a repeated code with a different
 * category fails loading.</li>
 * </ul>
 *
 * <p>File, header and column-count checks are done by {@link CsvFileReader}. Line numbers in messages are 1-based
 * file lines (the header is line 1), assuming one record per line.
 */
public class ProductCsvLoader {

	static final String PRODUCT_CODE = "product_code";
	static final String PRODUCT_NAME = "product_name";
	static final String CATEGORY = "category";

	private static final List<String> REQUIRED_COLUMNS = List.of(PRODUCT_CODE, PRODUCT_NAME, CATEGORY);

	private static final Logger log = LoggerFactory.getLogger(ProductCsvLoader.class);

	private final CsvFileReader reader = new CsvFileReader("Product", REQUIRED_COLUMNS, PRODUCT_CODE,
			ProductDataException::new);

	public Map<String, Product> load(Path file) {
		Map<String, Product> products = new HashMap<>();
		// First line each code appeared on, for duplicate messages.
		Map<String, Long> lineByCode = new HashMap<>();

		// CsvFileReader handles file, header and column-count checks; this callback validates each row.
		reader.read(file, row -> {
			// Key is used exactly as written ("ABC" and "abc" are different products).
			String code = row.get(PRODUCT_CODE);
			if (code.isBlank()) {
				throw row.error("product_code is blank");
			}

			// Category.parse ignores case and surrounding spaces; unknown values fail.
			String rawCategory = row.get(CATEGORY);
			Category category = Category.parse(rawCategory)
				.orElseThrow(() -> row.error("unrecognized category '" + rawCategory + "'"));

			// Duplicate code: a conflicting category fails (we can't tell which is right); a matching one is
			// skipped with a WARN, keeping the first.
			Product existing = products.get(code);
			if (existing != null) {
				long firstLine = lineByCode.get(code);
				if (existing.category() != category) {
					throw new ProductDataException(row.file() + ": product_code '" + code
							+ "' has conflicting categories: '" + existing.category().label() + "' on line " + firstLine
							+ " and '" + category.label() + "' on line " + row.line());
				}
				log.warn("Skipping duplicate product_code '{}' on line {} (first seen on line {})", code, row.line(),
						firstLine);
				return;
			}

			products.put(code, new Product(code, row.get(PRODUCT_NAME), category));
			lineByCode.put(code, row.line());
		});

		log.info("Loaded {} products from {}", products.size(), file.toAbsolutePath().normalize());
		// Immutable so callers can't change the products after loading.
		return Map.copyOf(products);
	}

}

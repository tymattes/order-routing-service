package com.tylermatteson.orderrouting.supplier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tylermatteson.orderrouting.csv.CsvFileReader;
import com.tylermatteson.orderrouting.csv.CsvRow;
import com.tylermatteson.orderrouting.product.Category;

/**
 * Parses a suppliers CSV file (header
 * {@code supplier_id,suplier_name,service_zips,product_categories,customer_satisfaction_score,can_mail_order?},
 * including the {@code suplier_name} typo) into an immutable map keyed by supplier id, iterating in file order.
 *
 * <p>Rules (any violation fails loading):
 * <ul>
 * <li>{@code supplier_id} is used exactly as written: no trimming, no case change. Blank or repeated ids fail.</li>
 * <li>{@code suplier_name} is kept as-is.</li>
 * <li>{@code product_categories} is a comma-separated list; each value is normalized with {@link Category#parse}
 * and must be a known category. Empty values and an empty list fail.</li>
 * <li>{@code customer_satisfaction_score} is trimmed; {@code no ratings yet} (any case) means no score. Otherwise it
 * must be a plain decimal number ({@code 9}, {@code 9.0}, {@code 7.8}) in {@code [1, 10]}; it is never clamped.</li>
 * <li>{@code can_mail_order?} is trimmed; {@code y} or {@code n} in any case.</li>
 * <li>{@code service_zips} is a comma-separated list of tokens, each a zip ({@code 1-5} digits) or a range
 * {@code start-end}. Zips are parsed as integers so {@code 2110} equals {@code 02110}. Empty tokens, an empty list,
 * malformed tokens and inverted ranges fail.</li>
 * </ul>
 *
 * <p>Row errors name the file, line number, supplier id and the offending raw value.
 */
public class SupplierCsvLoader {

	static final String SUPPLIER_ID = "supplier_id";
	/** Spelled as in the source data. */
	static final String SUPPLIER_NAME = "suplier_name";
	static final String SERVICE_ZIPS = "service_zips";
	static final String PRODUCT_CATEGORIES = "product_categories";
	static final String SATISFACTION_SCORE = "customer_satisfaction_score";
	static final String CAN_MAIL_ORDER = "can_mail_order?";

	static final String NO_RATINGS_YET = "no ratings yet";
	static final double MIN_SCORE = 1.0;
	static final double MAX_SCORE = 10.0;

	private static final List<String> REQUIRED_COLUMNS = List.of(SUPPLIER_ID, SUPPLIER_NAME, SERVICE_ZIPS,
			PRODUCT_CATEGORIES, SATISFACTION_SCORE, CAN_MAIL_ORDER);

	/** Plain decimal only, so Java-isms like {@code 9d}, {@code 1e1} or {@code NaN} are rejected. */
	private static final Pattern SCORE = Pattern.compile("\\d+(?:\\.\\d+)?");

	private static final Pattern ZIP_TOKEN = Pattern.compile("(\\d{1,5})(?:-(\\d{1,5}))?");

	private static final Logger log = LoggerFactory.getLogger(SupplierCsvLoader.class);

	private final CsvFileReader reader = new CsvFileReader("Supplier", REQUIRED_COLUMNS, SUPPLIER_ID,
			SupplierDataException::new);

	public Map<String, Supplier> load(Path file) {
		Map<String, Supplier> suppliers = new LinkedHashMap<>();
		Map<String, Long> lineById = new HashMap<>();

		reader.read(file, row -> {
			String id = row.get(SUPPLIER_ID);
			if (id.isBlank()) {
				throw row.error("supplier_id is blank: '" + id + "'");
			}
			Long firstLine = lineById.putIfAbsent(id, row.line());
			if (firstLine != null) {
				throw row.error("duplicate supplier_id '" + id + "' on line " + row.line() + " (first seen on line "
						+ firstLine + ")");
			}

			suppliers.put(id, new Supplier(id, row.get(SUPPLIER_NAME), parseServiceZips(row), parseCategories(row),
					parseScore(row), parseCanMailOrder(row)));
		});

		log.info("Loaded {} suppliers from {}", suppliers.size(), file.toAbsolutePath().normalize());
		return Collections.unmodifiableMap(suppliers);
	}

	private static Set<Category> parseCategories(CsvRow row) {
		String raw = row.get(PRODUCT_CATEGORIES);
		if (raw.isBlank()) {
			throw row.error("product_categories is empty: '" + raw + "'");
		}
		Set<Category> categories = EnumSet.noneOf(Category.class);
		for (String token : raw.split(",", -1)) {
			if (token.isBlank()) {
				throw row.error("empty value in product_categories '" + raw + "'");
			}
			categories.add(Category.parse(token)
				.orElseThrow(() -> row.error("unrecognized category '" + token.trim() + "' in product_categories '"
						+ raw + "'")));
		}
		return categories;
	}

	private static OptionalDouble parseScore(CsvRow row) {
		String raw = row.get(SATISFACTION_SCORE);
		String value = raw.trim();
		if (value.equalsIgnoreCase(NO_RATINGS_YET)) {
			return OptionalDouble.empty();
		}
		if (!SCORE.matcher(value).matches()) {
			throw row.error("customer_satisfaction_score '" + raw + "' is not a number or '" + NO_RATINGS_YET + "'");
		}
		double score = Double.parseDouble(value);
		if (!Double.isFinite(score) || score < MIN_SCORE || score > MAX_SCORE) {
			throw row.error("customer_satisfaction_score '" + raw + "' is outside [" + MIN_SCORE + ", " + MAX_SCORE
					+ "]");
		}
		return OptionalDouble.of(score);
	}

	private static boolean parseCanMailOrder(CsvRow row) {
		String raw = row.get(CAN_MAIL_ORDER);
		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "y" -> true;
			case "n" -> false;
			default -> throw row.error("can_mail_order? '" + raw + "' must be 'y' or 'n'");
		};
	}

	private static ZipCoverage parseServiceZips(CsvRow row) {
		String raw = row.get(SERVICE_ZIPS);
		if (raw.isBlank()) {
			throw row.error("service_zips is empty: '" + raw + "'");
		}
		Set<Integer> zips = new HashSet<>();
		List<ZipRange> ranges = new ArrayList<>();
		for (String rawToken : raw.split(",", -1)) {
			String token = rawToken.trim();
			Matcher matcher = ZIP_TOKEN.matcher(token);
			if (!matcher.matches()) {
				throw row.error("invalid service_zips value '" + token + "' in '" + raw
						+ "' (expected a 1-5 digit zip or a start-end range)");
			}
			int start = Integer.parseInt(matcher.group(1));
			if (matcher.group(2) == null) {
				zips.add(start);
				continue;
			}
			int end = Integer.parseInt(matcher.group(2));
			if (start > end) {
				throw row.error("inverted service_zips range '" + token + "' in '" + raw + "' (start after end)");
			}
			ranges.add(new ZipRange(start, end));
		}
		return new ZipCoverage(zips, ranges);
	}

}

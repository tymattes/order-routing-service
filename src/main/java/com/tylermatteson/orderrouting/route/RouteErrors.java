package com.tylermatteson.orderrouting.route;

import java.util.Locale;

/**
 * Error messages returned in {@code RouteResponse.errors}. Parameterized messages are {@link String#format} templates;
 * use the helper methods to fill them. Line numbers are 1-based.
 */
public final class RouteErrors {

	/** {@code items} is missing or empty. */
	public static final String NO_LINE_ITEMS = "Order must include at least one line item.";

	/** {@code customer_zip} is missing, blank, or not accepted by the order-zip normalizer. */
	public static final String INVALID_CUSTOMER_ZIP = "Order must include a valid customer_zip.";

	/** {@code order_id} is missing or blank. */
	public static final String MISSING_ORDER_ID = "Order must include an order_id.";

	/** {@code mail_order} is missing or {@code null}. */
	public static final String MISSING_MAIL_ORDER = "Order must include mail_order (true or false).";

	/** Template: 1-based line number. */
	public static final String MISSING_PRODUCT_CODE = "Line item %d must include a product_code.";

	/** Template: 1-based line number. Quantity is missing, zero or negative. */
	public static final String INVALID_QUANTITY = "Line item %d must have a quantity of at least 1.";

	/** Template: the normalized (trimmed, upper-case) product code. */
	public static final String UNKNOWN_PRODUCT_CODE = "Unknown product_code: %s.";

	/** The body is not parseable JSON, or a field has the wrong type. */
	public static final String MALFORMED_REQUEST = "Request body must be a valid JSON order.";

	/** The request {@code Content-Type} is missing or not {@code application/json}. */
	public static final String UNSUPPORTED_CONTENT_TYPE = "Content-Type must be application/json.";

	/** Template: normalized product code, category label, customer_zip as given. */
	public static final String NO_FEASIBLE_SUPPLIER = "No supplier can fulfill product_code %s (%s) for customer_zip %s.";

	private RouteErrors() {
	}

	public static String missingProductCode(int line) {
		return String.format(Locale.ROOT, MISSING_PRODUCT_CODE, line);
	}

	public static String invalidQuantity(int line) {
		return String.format(Locale.ROOT, INVALID_QUANTITY, line);
	}

	public static String unknownProductCode(String productCode) {
		return String.format(Locale.ROOT, UNKNOWN_PRODUCT_CODE, productCode);
	}

	public static String noFeasibleSupplier(String productCode, String categoryLabel, String customerZip) {
		return String.format(Locale.ROOT, NO_FEASIBLE_SUPPLIER, productCode, categoryLabel, customerZip);
	}

}

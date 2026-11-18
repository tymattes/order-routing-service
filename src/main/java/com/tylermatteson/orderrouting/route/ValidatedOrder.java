package com.tylermatteson.orderrouting.route;

import java.util.List;
import java.util.Objects;

import com.tylermatteson.orderrouting.product.Product;

/**
 * An order that passed {@link RouteRequestValidator}: every field is present and every product code is known.
 *
 * @param orderId     the order id
 * @param customerZip the customer zip exactly as given, for error messages
 * @param zip         the normalized zip, comparable with {@code ZipCoverage.covers}
 * @param mailOrder   whether mail-order fulfillment is allowed
 * @param lines       order lines, in request order (at least one)
 */
public record ValidatedOrder(String orderId, String customerZip, int zip, boolean mailOrder, List<Line> lines) {

	public ValidatedOrder {
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(customerZip, "customerZip");
		lines = List.copyOf(lines);
	}

	/**
	 * One validated order line.
	 *
	 * @param product  the catalog product (its code is the normalized request code)
	 * @param quantity units ordered, at least 1
	 */
	public record Line(Product product, int quantity) {

		public Line {
			Objects.requireNonNull(product, "product");
		}

	}

}

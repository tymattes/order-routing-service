package com.tylermatteson.orderrouting.route;

import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Body of {@code POST /api/route}, exactly as received. Unknown fields (for example {@code priority} and
 * {@code notes} in the sample orders) are ignored. Fields are boxed so a missing value can be told apart from a
 * default; {@link RouteRequestValidator} checks and normalizes them.
 *
 * @param orderId     caller's order id
 * @param customerZip delivery zip: 1-5 digits or ZIP+4
 * @param mailOrder   whether the customer accepts mail-order fulfillment
 * @param items       line items, in request order
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RouteRequest(String orderId, String customerZip, Boolean mailOrder, List<LineItem> items) {

	/**
	 * One line of an incoming order, exactly as received.
	 *
	 * @param productCode the product code as sent (not yet trimmed or upper-cased)
	 * @param quantity    units ordered
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record LineItem(String productCode, Integer quantity) {
	}

}

package com.tylermatteson.orderrouting.route;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tylermatteson.orderrouting.product.Category;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Body returned by {@code POST /api/route}. Exactly one of {@code routing} and {@code errors} is present; the other is
 * left out of the JSON. Build with {@link #success} or {@link #failure}.
 *
 * @param feasible whether the order can be routed
 * @param routing  shipments, ordered by where each supplier's first item appears in the request; {@code null} when
 *                 not feasible
 * @param errors   validation or feasibility errors; {@code null} when feasible
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteResponse(boolean feasible, List<Shipment> routing, List<String> errors) {

	public static RouteResponse success(List<Shipment> routing) {
		return new RouteResponse(true, List.copyOf(routing), null);
	}

	public static RouteResponse failure(List<String> errors) {
		if (errors.isEmpty()) {
			throw new IllegalArgumentException("a failure needs at least one error");
		}
		return new RouteResponse(false, null, List.copyOf(errors));
	}

	/**
	 * The items sent to one supplier.
	 *
	 * @param supplierId   the supplier's id
	 * @param supplierName the supplier's display name
	 * @param items        the routed items, in request order
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Shipment(String supplierId, String supplierName, List<RoutedItem> items) {

		public Shipment {
			Objects.requireNonNull(supplierId, "supplierId");
			Objects.requireNonNull(supplierName, "supplierName");
			items = List.copyOf(items);
		}

	}

	/**
	 * One order line as routed to a supplier.
	 *
	 * @param productCode     the normalized product code
	 * @param quantity        units ordered
	 * @param category        the product's category label (lower-case, for example {@code "cpap"})
	 * @param fulfillmentMode how the supplier delivers it
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record RoutedItem(String productCode, int quantity, String category, FulfillmentMode fulfillmentMode) {

		public RoutedItem {
			Objects.requireNonNull(productCode, "productCode");
			Objects.requireNonNull(category, "category");
			Objects.requireNonNull(fulfillmentMode, "fulfillmentMode");
		}

		public RoutedItem(String productCode, int quantity, Category category, FulfillmentMode fulfillmentMode) {
			this(productCode, quantity, category.label(), fulfillmentMode);
		}

	}

	/**
	 * How a routed item reaches the customer. A supplier that serves the customer's zip is {@link #LOCAL}, even if it
	 * can also mail order.
	 */
	public enum FulfillmentMode {

		@JsonProperty("local")
		LOCAL,

		@JsonProperty("mail_order")
		MAIL_ORDER

	}

}

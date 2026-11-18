package com.tylermatteson.orderrouting.route;

import java.util.Optional;

import com.tylermatteson.orderrouting.product.Category;
import com.tylermatteson.orderrouting.route.RouteResponse.FulfillmentMode;
import com.tylermatteson.orderrouting.supplier.Supplier;

/**
 * Routing rule 1 (feasibility): whether a supplier may take an item, and how it would deliver it.
 */
final class SupplierEligibility {

	private SupplierEligibility() {
	}

	/**
	 * Decides how {@code supplier} could deliver an item of {@code category} to {@code zip}.
	 *
	 * @param zip       the normalized customer zip
	 * @param mailOrder whether the order allows mail-order fulfillment
	 * @return {@link FulfillmentMode#LOCAL} if the supplier carries the category and serves the zip (even if it can
	 *         also mail), {@link FulfillmentMode#MAIL_ORDER} if it carries the category, mail order is allowed and it can
	 *         mail order, otherwise empty
	 */
	static Optional<FulfillmentMode> modeFor(Supplier supplier, Category category, int zip, boolean mailOrder) {
		// A supplier that doesn't carry the category can't take the item by any mode.
		if (!supplier.categories().contains(category)) {
			return Optional.empty();
		}
		// Local is checked first so it wins over mail order when the supplier can do both; it doesn't depend on the
		// order's mailOrder flag.
		if (supplier.serviceZips().covers(zip)) {
			return Optional.of(FulfillmentMode.LOCAL);
		}
		// Out of area: mail order needs both the customer's consent and the supplier's capability.
		if (mailOrder && supplier.canMailOrder()) {
			return Optional.of(FulfillmentMode.MAIL_ORDER);
		}
		return Optional.empty();
	}

}

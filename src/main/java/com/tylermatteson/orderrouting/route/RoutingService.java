package com.tylermatteson.orderrouting.route;

/**
 * Chooses suppliers for a validated order.
 */
public interface RoutingService {

	/**
	 * Routes {@code order}.
	 *
	 * @return {@link RouteResponse#success} with the shipments, or {@link RouteResponse#failure} with one
	 *         {@link RouteErrors#NO_FEASIBLE_SUPPLIER} error per item that no supplier can fulfill
	 */
	RouteResponse route(ValidatedOrder order);

}

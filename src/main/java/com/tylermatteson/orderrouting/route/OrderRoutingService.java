package com.tylermatteson.orderrouting.route;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tylermatteson.orderrouting.config.AppProperties;
import com.tylermatteson.orderrouting.product.Category;
import com.tylermatteson.orderrouting.product.Product;
import com.tylermatteson.orderrouting.route.MinimumShipmentRouter.Candidate;
import com.tylermatteson.orderrouting.supplier.Supplier;
import com.tylermatteson.orderrouting.supplier.SupplierRepository;

/**
 * Routes orders by the rules in the README's "Routing criteria": {@link SupplierEligibility} finds each line's
 * eligible suppliers (rule 1), then {@link MinimumShipmentRouter} picks the plan (rules 2-5).
 */
@Service
public class OrderRoutingService implements RoutingService {

	private final SupplierRepository suppliers;

	private final MinimumShipmentRouter router;

	public OrderRoutingService(SupplierRepository suppliers, AppProperties properties) {
		this.suppliers = suppliers;
		// The router is a plain object, not a bean: it is stateless apart from the configured tolerance.
		this.router = new MinimumShipmentRouter(properties.routing().ratingTolerance());
	}

	@Override
	public RouteResponse route(ValidatedOrder order) {
		// Caches the repository lookup per category, so an order with several items of one category queries once.
		Map<Category, List<Supplier>> byCategory = new EnumMap<>(Category.class);
		List<List<Candidate>> candidates = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (ValidatedOrder.Line line : order.lines()) {
			Product product = line.product();
			Category category = product.category();
			List<Candidate> eligible = new ArrayList<>();
			for (Supplier supplier : byCategory.computeIfAbsent(category, suppliers::findByCategory)) {
				SupplierEligibility.modeFor(supplier, category, order.zip(), order.mailOrder())
					.ifPresent(mode -> eligible.add(new Candidate(supplier, mode)));
			}
			if (eligible.isEmpty()) {
				// Keep going rather than failing fast, so the response lists every unroutable item at once.
				errors.add(RouteErrors.noFeasibleSupplier(product.code(), category.label(), order.customerZip()));
			}
			candidates.add(eligible);
		}
		// All-or-nothing: if any line is infeasible the order is not partially routed.
		if (!errors.isEmpty()) {
			return RouteResponse.failure(errors);
		}
		// Every line now has at least one candidate, which is the router's precondition.
		return RouteResponse.success(router.route(order.lines(), candidates));
	}

}

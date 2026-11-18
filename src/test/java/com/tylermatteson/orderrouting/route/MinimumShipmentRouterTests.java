package com.tylermatteson.orderrouting.route;

import static com.tylermatteson.orderrouting.route.RouteResponse.FulfillmentMode.LOCAL;
import static com.tylermatteson.orderrouting.route.RouteResponse.FulfillmentMode.MAIL_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tylermatteson.orderrouting.product.Category;
import com.tylermatteson.orderrouting.product.Product;
import com.tylermatteson.orderrouting.route.MinimumShipmentRouter.Candidate;
import com.tylermatteson.orderrouting.route.RouteResponse.RoutedItem;
import com.tylermatteson.orderrouting.route.RouteResponse.Shipment;
import com.tylermatteson.orderrouting.route.ValidatedOrder.Line;
import com.tylermatteson.orderrouting.supplier.Supplier;
import com.tylermatteson.orderrouting.supplier.ZipCoverage;

/**
 * Unit tests for {@link MinimumShipmentRouter}. Suppliers are built by hand; only their id and score matter to the
 * router, because eligibility and fulfillment mode come in with each {@link Candidate}.
 */
class MinimumShipmentRouterTests {

	private static final double TOLERANCE = 0.5;

	private final MinimumShipmentRouter router = new MinimumShipmentRouter(TOLERANCE);

	private static final Line WHEELCHAIR = line("WC-1", Category.WHEELCHAIR);

	private static final Line CANE = line("CN-1", Category.CANE);

	private static final Line WALKER = line("WK-1", Category.WALKER);

	// --- rule 2: fewest shipments ---

	@Test
	void oneGeneralistBeatsHigherRatedSpecialists() {
		Supplier generalist = rated("SUP-G", 6.0);
		Supplier wheelchairs = rated("SUP-A", 10.0);
		Supplier canes = rated("SUP-B", 10.0);

		List<Shipment> routing = router.route(List.of(WHEELCHAIR, CANE),
				List.of(List.of(local(wheelchairs), local(generalist)), List.of(local(canes), local(generalist))));

		assertThat(routing).extracting(Shipment::supplierId).containsExactly("SUP-G");
		assertThat(routing.get(0).items()).extracting(RoutedItem::productCode).containsExactly("WC-1", "CN-1");
	}

	// --- rule 4 vs rule 3: tolerance ---

	@Test
	void localWinsWithinTolerance() {
		assertThat(singleLineWinner(TOLERANCE, local(rated("SUP-L", 8.7)), mail(rated("SUP-M", 9.0))))
			.isEqualTo("SUP-L");
	}

	@Test
	void mailWinsOutsideTolerance() {
		assertThat(singleLineWinner(TOLERANCE, local(rated("SUP-L", 8.4)), mail(rated("SUP-M", 9.0))))
			.isEqualTo("SUP-M");
	}

	@Test
	void localWinsAtExactlyTheTolerance() {
		assertThat(singleLineWinner(TOLERANCE, local(rated("SUP-L", 8.5)), mail(rated("SUP-M", 9.0))))
			.isEqualTo("SUP-L");
	}

	@Test
	void zeroToleranceLetsLocalWinOnlyAtEqualRating() {
		assertThat(singleLineWinner(0, local(rated("SUP-L", 8.9)), mail(rated("SUP-M", 9.0)))).isEqualTo("SUP-M");
		assertThat(singleLineWinner(0, local(rated("SUP-L", 9.0)), mail(rated("SUP-M", 9.0)))).isEqualTo("SUP-L");
	}

	// --- rule 3: unrated suppliers ---

	@Test
	void fullyRatedPlanBeatsHigherRatedPlanWithAnUnratedSupplier() {
		// {A, U} rates 10 (mean over its rated suppliers) but has an unrated supplier; {A, C} rates 7.5 and wins,
		// even though U is local and C ships by mail.
		Supplier a = rated("SUP-A", 10.0);
		Supplier u = unrated("SUP-U");
		Supplier c = rated("SUP-C", 5.0);

		List<Shipment> routing = router.route(List.of(WHEELCHAIR, CANE),
				List.of(List.of(local(a)), List.of(local(u), mail(c))));

		assertThat(routing).extracting(Shipment::supplierId).containsExactly("SUP-A", "SUP-C");
		assertThat(routing.get(1).items()).extracting(RoutedItem::fulfillmentMode).containsExactly(MAIL_ORDER);
	}

	@Test
	void fewerUnratedSuppliersWins() {
		// {U1, U2} is all local but has two unrated suppliers; {R, U2} has one, so it wins although R is rated low
		// and ships by mail.
		Supplier u1 = unrated("SUP-U1");
		Supplier u2 = unrated("SUP-U2");
		Supplier r = rated("SUP-R", 1.0);

		List<Shipment> routing = router.route(List.of(WHEELCHAIR, CANE),
				List.of(List.of(local(u1), mail(r)), List.of(local(u2))));

		assertThat(routing).extracting(Shipment::supplierId).containsExactly("SUP-R", "SUP-U2");
	}

	@Test
	void allUnratedTiesGoToLocalThenLowestId() {
		assertThat(singleLineWinner(TOLERANCE, mail(unrated("SUP-A")), local(unrated("SUP-C")),
				local(unrated("SUP-B"))))
			.isEqualTo("SUP-B");
	}

	// --- rule 5 and the final rating tie-break ---

	@Test
	void ratingBreaksATieAfterTheLocalCount() {
		// Both local and within tolerance: the higher rating wins before the id.
		assertThat(singleLineWinner(TOLERANCE, local(rated("SUP-A", 8.8)), local(rated("SUP-B", 9.0))))
			.isEqualTo("SUP-B");
	}

	@Test
	void lowestIdBreaksAFullTie() {
		assertThat(singleLineWinner(TOLERANCE, local(rated("SUP-B", 9.0)), local(rated("SUP-A", 9.0))))
			.isEqualTo("SUP-A");
	}

	// --- per-item assignment and output order ---

	@Test
	void itemSharedByTwoPlanSuppliersGoesToTheBestByLocalThenRatingThenId() {
		// Only {A, B} covers all three lines with two shipments. Both can take the walker line.
		Supplier a = rated("SUP-A", 9.0);
		Supplier b = rated("SUP-B", 7.0);

		// Walker: A by mail, B local -> B (local first).
		List<Shipment> localFirst = router.route(List.of(WHEELCHAIR, CANE, WALKER),
				List.of(List.of(mail(a)), List.of(local(b)), List.of(mail(a), local(b))));
		assertThat(localFirst).extracting(Shipment::supplierId, MinimumShipmentRouterTests::productCodes)
			.containsExactly(tuple("SUP-A", List.of("WC-1")), tuple("SUP-B", List.of("CN-1", "WK-1")));

		// Walker: both local -> A (higher rating).
		List<Shipment> ratingNext = router.route(List.of(WHEELCHAIR, CANE, WALKER),
				List.of(List.of(local(a)), List.of(local(b)), List.of(local(a), local(b))));
		assertThat(ratingNext).extracting(Shipment::supplierId, MinimumShipmentRouterTests::productCodes)
			.containsExactly(tuple("SUP-A", List.of("WC-1", "WK-1")), tuple("SUP-B", List.of("CN-1")));

		// Walker: both local, same rating -> A (lower id).
		Supplier b9 = rated("SUP-B", 9.0);
		List<Shipment> idLast = router.route(List.of(WHEELCHAIR, CANE, WALKER),
				List.of(List.of(local(a)), List.of(local(b9)), List.of(local(b9), local(a))));
		assertThat(idLast).extracting(Shipment::supplierId, MinimumShipmentRouterTests::productCodes)
			.containsExactly(tuple("SUP-A", List.of("WC-1", "WK-1")), tuple("SUP-B", List.of("CN-1")));
	}

	@Test
	void shipmentsAreOrderedByTheirFirstItemAndCarryItemDetails() {
		// The higher id gets the first line, so shipment order follows the request, not the id.
		Supplier z = rated("SUP-Z", 9.0);
		Supplier a = rated("SUP-A", 9.0);
		Line wheelchairs = new Line(new Product("WC-1", "Wheelchair", Category.WHEELCHAIR), 2);

		List<Shipment> routing = router.route(List.of(wheelchairs, CANE, WALKER),
				List.of(List.of(mail(z)), List.of(local(a)), List.of(mail(z))));

		assertThat(routing).extracting(Shipment::supplierId).containsExactly("SUP-Z", "SUP-A");
		assertThat(routing.get(0).supplierName()).isEqualTo("Name of SUP-Z");
		assertThat(routing.get(0).items()).containsExactly(new RoutedItem("WC-1", 2, Category.WHEELCHAIR, MAIL_ORDER),
				new RoutedItem("WK-1", 1, Category.WALKER, MAIL_ORDER));
		assertThat(routing.get(1).items()).containsExactly(new RoutedItem("CN-1", 1, Category.CANE, LOCAL));
	}

	// --- helpers ---

	private static String singleLineWinner(double tolerance, Candidate... candidates) {
		List<Shipment> routing = new MinimumShipmentRouter(tolerance).route(List.of(WHEELCHAIR),
				List.of(List.of(candidates)));
		assertThat(routing).hasSize(1);
		return routing.get(0).supplierId();
	}

	private static List<String> productCodes(Shipment shipment) {
		return shipment.items().stream().map(RoutedItem::productCode).toList();
	}

	private static Line line(String code, Category category) {
		return new Line(new Product(code, code, category), 1);
	}

	private static Candidate local(Supplier supplier) {
		return new Candidate(supplier, LOCAL);
	}

	private static Candidate mail(Supplier supplier) {
		return new Candidate(supplier, MAIL_ORDER);
	}

	private static Supplier rated(String id, double score) {
		return supplier(id, OptionalDouble.of(score));
	}

	private static Supplier unrated(String id) {
		return supplier(id, OptionalDouble.empty());
	}

	private static Supplier supplier(String id, OptionalDouble score) {
		// Zips and categories are irrelevant here: the candidate lists say who is eligible and how.
		return new Supplier(id, "Name of " + id, new ZipCoverage(Set.of(), List.of()), Set.of(Category.values()),
				score, true);
	}

}

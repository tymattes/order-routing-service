package com.tylermatteson.orderrouting.route;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;

import com.tylermatteson.orderrouting.route.RouteResponse.FulfillmentMode;
import com.tylermatteson.orderrouting.route.RouteResponse.RoutedItem;
import com.tylermatteson.orderrouting.route.RouteResponse.Shipment;
import com.tylermatteson.orderrouting.route.ValidatedOrder.Line;
import com.tylermatteson.orderrouting.supplier.Supplier;

/**
 * Routing rules 2-5: picks the plan (set of suppliers) for an order whose every line has at least one eligible
 * supplier, and assigns each line to one supplier in it.
 *
 * <p>Only minimum covers are considered: the smallest sets of suppliers that together can take every line. Among
 * those, the plan is chosen by, in order:
 * <ol>
 * <li>fewest unrated suppliers (so any plan with an unrated supplier ranks below every fully rated plan);</li>
 * <li>rating, the unweighted mean score of the plan's rated suppliers: plans within {@code ratingTolerance} of the
 * best rating stay in the running, the rest drop out;</li>
 * <li>most local suppliers;</li>
 * <li>highest rating, then the lexicographically smallest sorted list of supplier ids.</li>
 * </ol>
 * So local preference can outweigh a small rating difference, never a large one.
 *
 * <p>Within the chosen plan each line goes to the eligible plan supplier that is best by: local first, rated before
 * unrated, higher score, lower id. Shipments are ordered by where each supplier's first line appears in the request;
 * items within a shipment keep request order.
 *
 * <p>Plans are found by iterative-deepening depth-first search, which is exponential in the worst case but fast for
 * order-sized inputs.
 */
class MinimumShipmentRouter {

	/** Absorbs floating-point error in the rating comparison, so a difference of exactly the tolerance counts. */
	private static final double EPSILON = 1e-9;

	/** Orders plans of equal rank on everything else: smallest sorted id list first. */
	private static final Comparator<List<String>> IDS_ORDER = (a, b) -> {
		for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
			int c = a.get(i).compareTo(b.get(i));
			if (c != 0) {
				return c;
			}
		}
		return Integer.compare(a.size(), b.size());
	};

	private final double ratingTolerance;

	/**
	 * @param ratingTolerance how far below the best rating a plan may be and still win on local suppliers; at least 0
	 */
	MinimumShipmentRouter(double ratingTolerance) {
		if (ratingTolerance < 0 || Double.isNaN(ratingTolerance)) {
			throw new IllegalArgumentException("ratingTolerance must be >= 0, was " + ratingTolerance);
		}
		this.ratingTolerance = ratingTolerance;
	}

	/**
	 * A supplier that may take a line, and how it would deliver it.
	 */
	record Candidate(Supplier supplier, FulfillmentMode mode) {

		Candidate {
			Objects.requireNonNull(supplier, "supplier");
			Objects.requireNonNull(mode, "mode");
		}

	}

	/**
	 * Routes {@code lines}.
	 *
	 * @param lines      the order lines, in request order
	 * @param candidates per line (same order and size as {@code lines}), its eligible suppliers; each list non-empty
	 * @return the shipments, ordered by each supplier's first item in the request
	 */
	List<Shipment> route(List<Line> lines, List<List<Candidate>> candidates) {
		if (lines.isEmpty() || lines.size() != candidates.size()) {
			throw new IllegalArgumentException("need one candidate list per line, and at least one line");
		}
		for (int i = 0; i < candidates.size(); i++) {
			if (candidates.get(i).isEmpty()) {
				throw new IllegalArgumentException("line " + (i + 1) + " has no candidates");
			}
		}

		Index index = new Index(candidates);
		Plan best = select(minimumCovers(index));
		return assign(lines, index, best);
	}

	// --- enumeration (rule 2) ---

	/** Every minimum cover, keyed (and so deduplicated) by its sorted supplier ids. */
	private List<Plan> minimumCovers(Index index) {
		Map<List<String>, Plan> found = new TreeMap<>(IDS_ORDER);
		// Iterative deepening: try every plan of size 1, then 2, ... and stop at the first size that yields any
		// cover, so every plan found is minimum. k never needs to exceed the line count, because one supplier per
		// line always covers (each line has at least one candidate).
		for (int k = 1; k <= index.lineCount() && found.isEmpty(); k++) {
			search(index, new BitSet(index.lineCount()), new ArrayList<>(), k, found);
		}
		return List.copyOf(found.values());
	}

	private void search(Index index, BitSet covered, List<String> chosen, int k, Map<List<String>, Plan> found) {
		// Branch on the first uncovered line: any cover must include one of its candidates, so trying only those
		// keeps the search complete while pruning suppliers that can't help yet.
		int line = covered.nextClearBit(0);
		if (line >= index.lineCount()) {
			// Every line covered. The same set can be reached in different pick orders, so key by sorted ids.
			List<String> ids = chosen.stream().sorted().toList();
			found.computeIfAbsent(ids, key -> index.plan(key));
			return;
		}
		if (chosen.size() == k) {
			return;
		}
		for (Candidate candidate : index.candidates(line)) {
			String id = candidate.supplier().id();
			if (chosen.contains(id)) {
				continue;
			}
			// Picking a supplier covers every line it is eligible for, not just this one.
			BitSet next = (BitSet) covered.clone();
			next.or(index.coverage(id));
			chosen.add(id);
			search(index, next, chosen, k, found);
			chosen.remove(chosen.size() - 1); // backtrack
		}
	}

	// --- selection (rules 3-5) ---

	private Plan select(List<Plan> plans) {
		// Rule 3: keep only the plans with the fewest unrated suppliers.
		int fewestUnrated = plans.stream().mapToInt(Plan::unrated).min().orElseThrow();
		List<Plan> group = plans.stream().filter(p -> p.unrated() == fewestUnrated).toList();

		// Rule 4: drop plans rated more than ratingTolerance below the best. All plans in the group have the same size
		// (they are minimum covers) and the same unrated count, so either every plan has a rating or none does; that
		// is why getAsDouble() below is safe, and why an empty bestRating means there is nothing to filter on.
		OptionalDouble bestRating = group.stream()
			.map(Plan::rating)
			.filter(OptionalDouble::isPresent)
			.mapToDouble(OptionalDouble::getAsDouble)
			.max();
		List<Plan> withinTolerance = bestRating.isEmpty() ? group
				: group.stream()
					.filter(p -> p.rating().getAsDouble() >= bestRating.getAsDouble() - ratingTolerance - EPSILON)
					.toList();

		// Rule 5 and tie-breaks: most local suppliers, then highest rating, then smallest sorted id list.
		Comparator<Plan> order = Comparator.comparingInt(Plan::local)
			.reversed()
			.thenComparing(Comparator.comparingDouble((Plan p) -> p.rating().orElse(0)).reversed())
			.thenComparing(Plan::ids, IDS_ORDER);
		return withinTolerance.stream().min(order).orElseThrow();
	}

	// --- assignment ---

	private List<Shipment> assign(List<Line> lines, Index index, Plan plan) {
		// "Smallest" candidate wins: local before mail order, rated before unrated, higher score, then lower id.
		Comparator<Candidate> best = Comparator
			.comparing((Candidate c) -> c.mode() != FulfillmentMode.LOCAL)
			.thenComparing(c -> c.supplier().satisfactionScore().isEmpty())
			.thenComparing(Comparator
				.comparingDouble((Candidate c) -> c.supplier().satisfactionScore().orElse(0))
				.reversed())
			.thenComparing(c -> c.supplier().id());

		// LinkedHashMap keeps suppliers in the order of their first assigned line.
		Map<String, List<RoutedItem>> items = new LinkedHashMap<>();
		Map<String, Supplier> suppliers = new LinkedHashMap<>();
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			// Only plan suppliers may take the line; the plan is a cover, so at least one of them is eligible.
			Candidate chosen = index.candidates(i)
				.stream()
				.filter(c -> plan.ids().contains(c.supplier().id()))
				.min(best)
				.orElseThrow();
			String id = chosen.supplier().id();
			suppliers.putIfAbsent(id, chosen.supplier());
			items.computeIfAbsent(id, key -> new ArrayList<>())
				.add(new RoutedItem(line.product().code(), line.quantity(), line.product().category(),
						chosen.mode()));
		}

		List<Shipment> shipments = new ArrayList<>();
		items.forEach((id, routed) -> shipments.add(new Shipment(id, suppliers.get(id).name(), routed)));
		return shipments;
	}

	// --- model ---

	/**
	 * A candidate plan.
	 *
	 * @param ids     sorted supplier ids
	 * @param unrated how many of its suppliers have no score
	 * @param rating  the mean score of its rated suppliers; empty if none is rated
	 * @param local   how many of its suppliers deliver locally
	 */
	private record Plan(List<String> ids, int unrated, OptionalDouble rating, int local) {
	}

	/** Per-supplier lookups built from the candidate lists. */
	private static final class Index {

		private final List<List<Candidate>> candidates;

		private final Map<String, Supplier> suppliers = new LinkedHashMap<>();

		private final Map<String, BitSet> coverage = new LinkedHashMap<>();

		private final Map<String, Boolean> local = new LinkedHashMap<>();

		Index(List<List<Candidate>> candidates) {
			this.candidates = candidates;
			for (int line = 0; line < candidates.size(); line++) {
				for (Candidate candidate : candidates.get(line)) {
					String id = candidate.supplier().id();
					suppliers.putIfAbsent(id, candidate.supplier());
					coverage.computeIfAbsent(id, key -> new BitSet(candidates.size())).set(line);
					// A supplier's mode depends only on the zip, so it is the same on every line; OR-ing just
					// guards against callers that pass mixed modes.
					local.merge(id, candidate.mode() == FulfillmentMode.LOCAL, Boolean::logicalOr);
				}
			}
		}

		int lineCount() {
			return candidates.size();
		}

		List<Candidate> candidates(int line) {
			return candidates.get(line);
		}

		BitSet coverage(String id) {
			return coverage.get(id);
		}

		Plan plan(List<String> ids) {
			int unrated = 0;
			int localCount = 0;
			double sum = 0;
			int rated = 0;
			for (String id : ids) {
				OptionalDouble score = suppliers.get(id).satisfactionScore();
				if (score.isPresent()) {
					sum += score.getAsDouble();
					rated++;
				}
				else {
					unrated++;
				}
				if (local.get(id)) {
					localCount++;
				}
			}
			return new Plan(ids, unrated, rated == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / rated),
					localCount);
		}

	}

}

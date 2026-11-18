package com.tylermatteson.orderrouting.supplier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

import com.tylermatteson.orderrouting.product.Category;

/**
 * A supplier from the supplier data file.
 *
 * @param id                the supplier id exactly as it appears in the source data (never trimmed or case-changed)
 * @param name              the display name
 * @param serviceZips       zips the supplier serves locally
 * @param categories        product categories the supplier carries (immutable, enum order)
 * @param satisfactionScore customer satisfaction score in {@code [1, 10]}, or empty when the supplier has no ratings
 * @param canMailOrder      whether the supplier ships by mail; independent of {@code serviceZips}
 */
public record Supplier(String id, String name, ZipCoverage serviceZips, Set<Category> categories,
		OptionalDouble satisfactionScore, boolean canMailOrder) {

	public Supplier {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(serviceZips, "serviceZips");
		Objects.requireNonNull(categories, "categories");
		Objects.requireNonNull(satisfactionScore, "satisfactionScore");
		EnumSet<Category> copy = EnumSet.noneOf(Category.class);
		copy.addAll(categories);
		categories = Collections.unmodifiableSet(copy);
	}

}

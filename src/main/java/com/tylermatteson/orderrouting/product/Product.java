package com.tylermatteson.orderrouting.product;

import java.util.Objects;

/**
 * A product from the catalog.
 *
 * @param code     the product code exactly as it appears in the source data (never trimmed or case-changed)
 * @param name     the display name
 * @param category the product's category
 */
public record Product(String code, String name, Category category) {

	public Product {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(category, "category");
	}

}

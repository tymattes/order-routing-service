package com.tylermatteson.orderrouting.product;

import java.util.Map;
import java.util.Optional;

/**
 * {@link ProductRepository} backed by an immutable in-memory map keyed by product code.
 */
public class InMemoryProductRepository implements ProductRepository {

	private final Map<String, Product> productsByCode;

	public InMemoryProductRepository(Map<String, Product> productsByCode) {
		this.productsByCode = Map.copyOf(productsByCode);
	}

	@Override
	public Optional<Product> findByCode(String code) {
		// Map.copyOf rejects null keys on lookup, so handle null explicitly as "not found".
		return code == null ? Optional.empty() : Optional.ofNullable(productsByCode.get(code));
	}

	public int size() {
		return productsByCode.size();
	}

}

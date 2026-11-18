package com.tylermatteson.orderrouting.product;

import java.util.Optional;

/**
 * Read access to the product catalog, independent of where the products come from.
 */
public interface ProductRepository {

	/**
	 * Finds a product by its exact code. Matching is case-sensitive and does not trim whitespace, because
	 * inferring the wrong product is worse than finding none.
	 *
	 * @return the product, or {@link Optional#empty()} if no product has exactly this code
	 */
	Optional<Product> findByCode(String code);

}

package com.tylermatteson.orderrouting.supplier;

import java.util.List;
import java.util.Optional;

import com.tylermatteson.orderrouting.product.Category;

/**
 * Read access to suppliers, independent of where they come from.
 */
public interface SupplierRepository {

	/**
	 * Finds a supplier by its exact id. Matching is case-sensitive and does not trim whitespace.
	 *
	 * @return the supplier, or {@link Optional#empty()} if no supplier has exactly this id
	 */
	Optional<Supplier> findById(String id);

	/**
	 * Lists the suppliers carrying {@code category}, in source file order.
	 *
	 * @return an unmodifiable list, empty (never {@code null}) when no supplier carries the category
	 */
	List<Supplier> findByCategory(Category category);

}

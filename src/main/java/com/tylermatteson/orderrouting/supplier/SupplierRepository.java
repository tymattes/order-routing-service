package com.tylermatteson.orderrouting.supplier;

import java.util.List;

import com.tylermatteson.orderrouting.product.Category;

/**
 * Read access to suppliers, independent of where they come from.
 */
public interface SupplierRepository {

	/**
	 * Lists the suppliers carrying {@code category}, in source file order.
	 *
	 * @return an unmodifiable list, empty (never {@code null}) when no supplier carries the category
	 */
	List<Supplier> findByCategory(Category category);

}

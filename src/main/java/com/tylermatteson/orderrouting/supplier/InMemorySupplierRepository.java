package com.tylermatteson.orderrouting.supplier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.tylermatteson.orderrouting.product.Category;

/**
 * {@link SupplierRepository} backed by an immutable in-memory map keyed by supplier id, with a precomputed
 * category index. "File order" is the iteration order of the map passed in, which {@link SupplierCsvLoader}
 * guarantees.
 */
public class InMemorySupplierRepository implements SupplierRepository {

	private final Map<String, Supplier> suppliersById;

	private final Map<Category, List<Supplier>> suppliersByCategory;

	public InMemorySupplierRepository(Map<String, Supplier> suppliersById) {
		this.suppliersById = Collections.unmodifiableMap(new LinkedHashMap<>(suppliersById));

		Map<Category, List<Supplier>> index = new EnumMap<>(Category.class);
		for (Supplier supplier : this.suppliersById.values()) {
			for (Category category : supplier.categories()) {
				index.computeIfAbsent(category, key -> new ArrayList<>()).add(supplier);
			}
		}
		index.replaceAll((category, suppliers) -> List.copyOf(suppliers));
		this.suppliersByCategory = Collections.unmodifiableMap(index);
	}

	@Override
	public Optional<Supplier> findById(String id) {
		return id == null ? Optional.empty() : Optional.ofNullable(suppliersById.get(id));
	}

	@Override
	public List<Supplier> findByCategory(Category category) {
		return category == null ? List.of() : suppliersByCategory.getOrDefault(category, List.of());
	}

	public int size() {
		return suppliersById.size();
	}

}

package com.tylermatteson.orderrouting.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.tylermatteson.orderrouting.product.Category;

class InMemorySupplierRepositoryTests {

	private final SupplierRepository repository = new InMemorySupplierRepository(
			new SupplierCsvLoader().load(SupplierCsvLoaderTests.fixture("valid.csv")));

	@Test
	void findsSuppliersByCategoryInFileOrder() {
		assertThat(repository.findByCategory(Category.WHEELCHAIR)).extracting(Supplier::id)
			.containsExactly("SUP-001", "SUP-003");
		assertThat(repository.findByCategory(Category.CANE)).extracting(Supplier::id)
			.containsExactly("SUP-001", "SUP-002");
		assertThat(repository.findByCategory(Category.CPM_MACHINE)).extracting(Supplier::id)
			.containsExactly("SUP-004");
	}

	@Test
	void returnsEmptyListWhenNoSupplierCarriesCategory() {
		assertThat(repository.findByCategory(Category.OXYGEN)).isNotNull().isEmpty();
		assertThat(repository.findByCategory(null)).isNotNull().isEmpty();
	}

	@Test
	void returnsUnmodifiableLists() {
		assertThatThrownBy(() -> repository.findByCategory(Category.CANE).clear())
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> repository.findByCategory(Category.OXYGEN).add(null))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}

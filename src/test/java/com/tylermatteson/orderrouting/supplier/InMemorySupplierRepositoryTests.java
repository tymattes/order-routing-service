package com.tylermatteson.orderrouting.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tylermatteson.orderrouting.product.Category;

class InMemorySupplierRepositoryTests {

	private final SupplierRepository repository = new InMemorySupplierRepository(
			new SupplierCsvLoader().load(SupplierCsvLoaderTests.fixture("valid.csv")));

	@Test
	void findsSupplierByExactId() {
		assertThat(repository.findById("SUP-001"))
			.hasValueSatisfying(supplier -> assertThat(supplier.name()).isEqualTo("Alpha Medical Supply"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "sup-001", "Sup-001", " SUP-001", "SUP-001 ", "\tSUP-001\t" })
	void doesNotMatchDifferentCaseOrSurroundingWhitespace(String id) {
		assertThat(repository.findById(id)).isEmpty();
	}

	@Test
	void returnsEmptyForUnknownOrNullId() {
		assertThat(repository.findById("SUP-999")).isEmpty();
		assertThat(repository.findById(null)).isEmpty();
	}

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

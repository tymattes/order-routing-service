package com.tylermatteson.orderrouting.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InMemoryProductRepositoryTests {

	private final ProductRepository repository = new InMemoryProductRepository(
			new ProductCsvLoader().load(ProductCsvLoaderTests.fixture("valid.csv")));

	@Test
	void findsProductByExactCode() {
		assertThat(repository.findByCode("WC-STD-001"))
			.contains(new Product("WC-STD-001", "Standard Wheelchair", Category.WHEELCHAIR));
	}

	@ParameterizedTest
	@ValueSource(strings = { "wc-std-001", "Wc-Std-001", " WC-STD-001", "WC-STD-001 ", "\tWC-STD-001\t" })
	void doesNotMatchDifferentCaseOrSurroundingWhitespace(String code) {
		assertThat(repository.findByCode(code)).isEmpty();
	}

	@Test
	void returnsEmptyForUnknownCode() {
		assertThat(repository.findByCode("NO-SUCH-CODE")).isEmpty();
	}

	@Test
	void returnsEmptyForNullCode() {
		assertThat(repository.findByCode(null)).isEmpty();
	}

}

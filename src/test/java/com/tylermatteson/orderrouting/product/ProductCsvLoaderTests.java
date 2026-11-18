package com.tylermatteson.orderrouting.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ProductCsvLoaderTests {

	private final ProductCsvLoader loader = new ProductCsvLoader();

	static Path fixture(String name) {
		try {
			return Path.of(ProductCsvLoaderTests.class.getResource("/products/" + name).toURI());
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	void loadsValidFileAndNormalizesCategories() {
		Map<String, Product> products = loader.load(fixture("valid.csv"));

		assertThat(products).containsOnlyKeys("WC-STD-001", "CP-AUTO-001", "CPM-KNEE-001", "HB-FUL-001");
		assertThat(products.get("WC-STD-001"))
			.isEqualTo(new Product("WC-STD-001", "Standard Wheelchair", Category.WHEELCHAIR));
		assertThat(products.get("CP-AUTO-001").category()).isEqualTo(Category.CPAP);
		assertThat(products.get("CPM-KNEE-001").category()).isEqualTo(Category.CPM_MACHINE);
		assertThat(products.get("HB-FUL-001").name()).isEqualTo("Full Electric Bed, Deluxe");
	}

	@Test
	void returnsImmutableMap() {
		Map<String, Product> products = loader.load(fixture("valid.csv"));

		assertThatThrownBy(() -> products.remove("WC-STD-001")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void skipsSameCategoryDuplicateWithWarning(CapturedOutput output) {
		Map<String, Product> products = loader.load(fixture("duplicate-same-category.csv"));

		assertThat(products).containsOnlyKeys("WC-STD-001", "CP-AUTO-001");
		assertThat(output).contains("WARN")
			.contains("Skipping duplicate product_code 'WC-STD-001' on line 4 (first seen on line 2)");
	}

	@Test
	void failsOnDifferentCategoryDuplicate() {
		assertThatThrownBy(() -> loader.load(fixture("duplicate-different-category.csv")))
			.isInstanceOf(ProductDataException.class)
			.hasMessageContaining("'WC-STD-001'")
			.hasMessageContaining("'wheelchair' on line 2")
			.hasMessageContaining("'walker' on line 4");
	}

	@Test
	void failsOnUnknownCategory() {
		assertThatThrownBy(() -> loader.load(fixture("unknown-category.csv")))
			.isInstanceOf(ProductDataException.class)
			.hasMessageContaining("line 3")
			.hasMessageContaining("unrecognized category 'hoverboard'")
			.hasMessageContaining("'XX-UNK-001'");
	}

	@Test
	void failsOnBlankProductCode() {
		assertThatThrownBy(() -> loader.load(fixture("blank-code.csv")))
			.isInstanceOf(ProductDataException.class)
			.hasMessageContaining("line 3: product_code is blank");
	}

	@Test
	void failsOnMissingRequiredColumn() {
		assertThatThrownBy(() -> loader.load(fixture("missing-category-column.csv")))
			.isInstanceOf(ProductDataException.class)
			.hasMessageContaining("missing required column(s) [category]");
	}

	@Test
	void failsOnMissingFile(@TempDir Path dir) {
		Path missing = dir.resolve("products.csv");

		assertThatThrownBy(() -> loader.load(missing))
			.isInstanceOf(ProductDataException.class)
			.hasMessageContaining("Product data file not found")
			.hasMessageContaining(missing.toString());
	}

	@Test
	void parsesCrlfLineEndingsWithoutLeakingCarriageReturns() throws IOException {
		Path file = fixture("crlf.csv");
		// Guard against the fixture being silently converted to LF.
		assertThat(Files.readString(file)).contains("\r\n");

		Map<String, Product> products = loader.load(file);

		// product_name is the last column in this fixture and is not trimmed, so a leaked \r would show here.
		assertThat(products).containsOnlyKeys("WC-STD-001", "CP-AUTO-001");
		assertThat(products.get("WC-STD-001").name()).isEqualTo("Standard Wheelchair");
		assertThat(products.get("CP-AUTO-001"))
			.isEqualTo(new Product("CP-AUTO-001", "Auto CPAP", Category.CPAP));
	}

}

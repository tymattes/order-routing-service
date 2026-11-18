package com.tylermatteson.orderrouting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tylermatteson.orderrouting.product.Category;
import com.tylermatteson.orderrouting.product.InMemoryProductRepository;
import com.tylermatteson.orderrouting.product.ProductRepository;
import com.tylermatteson.orderrouting.supplier.InMemorySupplierRepository;
import com.tylermatteson.orderrouting.supplier.SupplierRepository;
import com.tylermatteson.orderrouting.supplier.ZipCoverage;

/**
 * Boots the full application against the real {@code data/products.csv} and {@code data/suppliers.csv} (default
 * {@code app.data-dir}).
 */
@SpringBootTest
class ReferenceDataLoadingTests {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Test
	void loadsRealProductCatalog() {
		// 1,200 rows minus 5 same-category duplicates.
		assertThat(productRepository).isInstanceOf(InMemoryProductRepository.class);
		assertThat(((InMemoryProductRepository) productRepository).size()).isEqualTo(1195);
		assertThat(productRepository.findByCode("WC-STD-001")).hasValueSatisfying(
				product -> assertThat(product.category()).isEqualTo(Category.WHEELCHAIR));
	}

	@Test
	void loadsRealSuppliers() {
		assertThat(supplierRepository).isInstanceOf(InMemorySupplierRepository.class);
		assertThat(((InMemorySupplierRepository) supplierRepository).size()).isEqualTo(1100);
		assertThat(supplierRepository.findById("SUP-T001")).isPresent();
		// SUP-016 lists 4-digit ranges ("2164-2213, ..."), so 2170 and 02170 must both be covered.
		assertThat(supplierRepository.findById("SUP-016")).hasValueSatisfying(supplier -> {
			assertThat(supplier.serviceZips().covers(2170)).isTrue();
			assertThat(supplier.serviceZips().covers(ZipCoverage.normalizeZip("02170").getAsInt())).isTrue();
		});
	}

}

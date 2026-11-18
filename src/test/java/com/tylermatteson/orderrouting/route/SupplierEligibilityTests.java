package com.tylermatteson.orderrouting.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tylermatteson.orderrouting.product.Category;
import com.tylermatteson.orderrouting.route.RouteResponse.FulfillmentMode;
import com.tylermatteson.orderrouting.supplier.Supplier;
import com.tylermatteson.orderrouting.supplier.ZipCoverage;

class SupplierEligibilityTests {

	private static final int ZIP = 10015;

	private static final int OTHER_ZIP = 20020;

	@Test
	void wrongCategoryIsNeverEligible() {
		Supplier supplier = supplier(ZIP, true, Category.CANE);

		assertThat(SupplierEligibility.modeFor(supplier, Category.WHEELCHAIR, ZIP, true)).isEmpty();
	}

	@Test
	void servingTheZipIsLocal() {
		Supplier supplier = supplier(ZIP, false, Category.WHEELCHAIR);

		assertThat(SupplierEligibility.modeFor(supplier, Category.WHEELCHAIR, ZIP, false))
			.contains(FulfillmentMode.LOCAL);
	}

	@Test
	void servingTheZipIsLocalEvenWhenTheSupplierCanMail() {
		Supplier supplier = supplier(ZIP, true, Category.WHEELCHAIR);

		assertThat(SupplierEligibility.modeFor(supplier, Category.WHEELCHAIR, ZIP, true))
			.contains(FulfillmentMode.LOCAL);
	}

	@Test
	void mailCapableSupplierOutsideTheZipIsMailWhenAllowed() {
		Supplier supplier = supplier(OTHER_ZIP, true, Category.WHEELCHAIR);

		assertThat(SupplierEligibility.modeFor(supplier, Category.WHEELCHAIR, ZIP, true))
			.contains(FulfillmentMode.MAIL_ORDER);
	}

	@Test
	void mailCapableSupplierOutsideTheZipIsIneligibleWhenMailIsNotAllowed() {
		Supplier supplier = supplier(OTHER_ZIP, true, Category.WHEELCHAIR);

		assertThat(SupplierEligibility.modeFor(supplier, Category.WHEELCHAIR, ZIP, false)).isEmpty();
	}

	@Test
	void neitherLocalNorMailCapableIsIneligible() {
		Supplier supplier = supplier(OTHER_ZIP, false, Category.WHEELCHAIR);

		assertThat(SupplierEligibility.modeFor(supplier, Category.WHEELCHAIR, ZIP, true)).isEmpty();
	}

	private static Supplier supplier(int zip, boolean canMailOrder, Category category) {
		return new Supplier("SUP-X", "Supplier X", new ZipCoverage(Set.of(zip), List.of()), Set.of(category),
				OptionalDouble.of(8.0), canMailOrder);
	}

}

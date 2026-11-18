package com.tylermatteson.orderrouting.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoryTests {

	@Test
	void hasExactlyTwentyFourCategories() {
		assertThat(Category.values()).hasSize(24);
	}

	@Test
	void looksUpByExactLabel() {
		assertThat(Category.fromLabel("hospital bed")).contains(Category.HOSPITAL_BED);
		assertThat(Category.fromLabel("cpm machine")).contains(Category.CPM_MACHINE);
		assertThat(Category.HOSPITAL_BED.label()).isEqualTo("hospital bed");
	}

	@Test
	void doesNotNormalizeOnLookup() {
		assertThat(Category.fromLabel("CPAP")).isEmpty();
		assertThat(Category.fromLabel(" cpap")).isEmpty();
		assertThat(Category.fromLabel(null)).isEmpty();
	}

	@Test
	void parseTrimsAndLowerCasesBeforeLookup() {
		assertThat(Category.parse("  CPAP ")).contains(Category.CPAP);
		assertThat(Category.parse("Hospital Bed")).contains(Category.HOSPITAL_BED);
		assertThat(Category.parse("cpm machine")).contains(Category.CPM_MACHINE);
	}

	@Test
	void parseReturnsEmptyForUnknownBlankOrNull() {
		assertThat(Category.parse("hoverboard")).isEmpty();
		assertThat(Category.parse("hospital  bed")).isEmpty();
		assertThat(Category.parse("")).isEmpty();
		assertThat(Category.parse(null)).isEmpty();
	}

}

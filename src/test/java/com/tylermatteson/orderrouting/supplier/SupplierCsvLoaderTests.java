package com.tylermatteson.orderrouting.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.tylermatteson.orderrouting.product.Category;

class SupplierCsvLoaderTests {

	private static final String HEADER = "supplier_id,suplier_name,service_zips,product_categories,"
			+ "customer_satisfaction_score,can_mail_order?";

	private final SupplierCsvLoader loader = new SupplierCsvLoader();

	@TempDir
	Path tempDir;

	static Path fixture(String name) {
		try {
			return Path.of(SupplierCsvLoaderTests.class.getResource("/suppliers/" + name).toURI());
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Writes a one-row suppliers file (id SUP-X, line 2) varying only the given fields, and returns its path. */
	private Path singleRow(String serviceZips, String categories, String score, String mailOrder) {
		Path file = tempDir.resolve("suppliers.csv");
		try {
			Files.writeString(file, HEADER + "\nSUP-X,Test Supplier," + quote(serviceZips) + "," + quote(categories)
					+ "," + quote(score) + "," + quote(mailOrder) + "\n");
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		return file;
	}

	private static String quote(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private Supplier loadSingle(String serviceZips, String categories, String score, String mailOrder) {
		return loader.load(singleRow(serviceZips, categories, score, mailOrder)).get("SUP-X");
	}

	// ---- whole-file behavior ----

	@Test
	void loadsValidFile() {
		Map<String, Supplier> suppliers = loader.load(fixture("valid.csv"));

		assertThat(suppliers.keySet()).containsExactly("SUP-001", "SUP-002", "SUP-003", "SUP-004");

		Supplier alpha = suppliers.get("SUP-001");
		assertThat(alpha.name()).isEqualTo("Alpha Medical Supply");
		assertThat(alpha.categories()).containsExactlyInAnyOrder(Category.WHEELCHAIR, Category.CANE);
		assertThat(alpha.satisfactionScore()).isEqualTo(OptionalDouble.of(9.0));
		assertThat(alpha.canMailOrder()).isTrue();
		assertThat(alpha.serviceZips().zips()).containsExactly(2110);
		assertThat(alpha.serviceZips().ranges()).containsExactly(new ZipRange(10001, 10099));

		Supplier gamma = suppliers.get("SUP-003");
		assertThat(gamma.satisfactionScore()).isEqualTo(OptionalDouble.of(7.8));
		assertThat(gamma.canMailOrder()).isFalse();
		assertThat(gamma.serviceZips().covers(1)).isTrue();

		assertThat(suppliers.get("SUP-004").serviceZips().ranges()).containsExactly(new ZipRange(100, 99999));
	}

	@Test
	void normalizesMixedCaseAndPaddedCategories() {
		Supplier beta = loader.load(fixture("valid.csv")).get("SUP-002");

		// Raw value: "Walker, CANE ,Hospital Bed"
		assertThat(beta.categories()).containsExactlyInAnyOrder(Category.WALKER, Category.CANE, Category.HOSPITAL_BED);
		assertThat(beta.satisfactionScore()).isEmpty();
	}

	@Test
	void returnsImmutableMapAndCategories() {
		Map<String, Supplier> suppliers = loader.load(fixture("valid.csv"));

		assertThatThrownBy(() -> suppliers.remove("SUP-001")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> suppliers.get("SUP-001").categories().add(Category.OXYGEN))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void parsesCrlfLineEndings() throws IOException {
		Path file = fixture("crlf.csv");
		// Guard against the fixture being silently converted to LF.
		assertThat(Files.readString(file)).contains("\r\n");

		Map<String, Supplier> suppliers = loader.load(file);

		// can_mail_order? is the last header column, so a leaked \r there would fail the required-column check.
		assertThat(suppliers.keySet()).containsExactly("SUP-001", "SUP-002");
		assertThat(suppliers.get("SUP-001").canMailOrder()).isTrue();
		assertThat(suppliers.get("SUP-002").canMailOrder()).isFalse();
		assertThat(suppliers.get("SUP-001").serviceZips().zips()).containsExactlyInAnyOrder(2110, 2111);
	}

	@Test
	void failsOnDuplicateSupplierIdNamingBothLines() {
		assertThatThrownBy(() -> loader.load(fixture("duplicate-id.csv")))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("duplicate-id.csv line 4")
			.hasMessageContaining("duplicate supplier_id 'SUP-001'")
			.hasMessageContaining("first seen on line 2");
	}

	@Test
	void failsOnUnknownCategory() {
		assertThatThrownBy(() -> loader.load(fixture("unknown-category.csv")))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("unknown-category.csv line 3")
			.hasMessageContaining("[supplier_id 'SUP-002']")
			.hasMessageContaining("unrecognized category 'hoverboard'")
			.hasMessageContaining("'cane, hoverboard'");
	}

	@Test
	void failsOnMissingRequiredColumn() {
		assertThatThrownBy(() -> loader.load(fixture("missing-column.csv")))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("missing required column(s) [can_mail_order?]");
	}

	@Test
	void failsOnWrongColumnCount() {
		assertThatThrownBy(() -> loader.load(fixture("wrong-column-count.csv")))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("line 3 [supplier_id 'SUP-002']")
			.hasMessageContaining("expected 6 columns but found 5");
	}

	@Test
	void failsOnMissingFile() {
		Path missing = tempDir.resolve("suppliers.csv");

		assertThatThrownBy(() -> loader.load(missing))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("Supplier data file not found")
			.hasMessageContaining(missing.toString());
	}

	@Test
	void failsOnBlankSupplierId() throws IOException {
		Path file = tempDir.resolve("suppliers.csv");
		Files.writeString(file, HEADER + "\n  ,Nameless,2110,cane,9,y\n");

		assertThatThrownBy(() -> loader.load(file))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("line 2: supplier_id is blank");
	}

	@Test
	void keepsSupplierIdExactlyAsWritten() throws IOException {
		Path file = tempDir.resolve("suppliers.csv");
		Files.writeString(file, HEADER + "\nsup-001 ,Padded,2110,cane,9,y\n");

		assertThat(loader.load(file)).containsOnlyKeys("sup-001 ");
	}

	// ---- product_categories ----

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", "cane,", ",cane", "cane,,walker", "cane, ,walker" })
	void failsOnEmptyCategoryListOrValue(String categories) {
		assertThatThrownBy(() -> loadSingle("2110", categories, "9", "y"))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("line 2 [supplier_id 'SUP-X']")
			.hasMessageContaining("product_categories")
			.hasMessageContaining("'" + categories + "'");
	}

	// ---- customer_satisfaction_score ----

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "9|9.0", "9.0|9.0", "7.8|7.8", "1|1.0", "10|10.0", "10.0|10.0",
			"' 8.5 '|8.5" })
	void acceptsScoresInRange(String raw, double expected) {
		assertThat(loadSingle("2110", "cane", raw, "y").satisfactionScore()).isEqualTo(OptionalDouble.of(expected));
	}

	@ParameterizedTest
	@ValueSource(strings = { "no ratings yet", "No Ratings Yet", "  NO RATINGS YET  " })
	void treatsNoRatingsYetAsEmpty(String raw) {
		assertThat(loadSingle("2110", "cane", raw, "y").satisfactionScore()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.9", "10.1", "0", "11", "-5" })
	void failsOnScoreOutOfRange(String raw) {
		assertThatThrownBy(() -> loadSingle("2110", "cane", raw, "y"))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("line 2 [supplier_id 'SUP-X']")
			.hasMessageContaining("customer_satisfaction_score '" + raw + "'");
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "", "NaN", "Infinity", "9d", "1e1", "9.", ".9", "9,0", "no ratings" })
	void failsOnScoreThatIsNotANumber(String raw) {
		assertThatThrownBy(() -> loadSingle("2110", "cane", raw, "y"))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("customer_satisfaction_score '" + raw + "' is not a number");
	}

	// ---- can_mail_order? ----

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "y|true", "Y|true", "' y '|true", "n|false", "N|false", "' N'|false" })
	void parsesMailOrderFlag(String raw, boolean expected) {
		assertThat(loadSingle("2110", "cane", "9", raw).canMailOrder()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "yes", "no", "true", "1", "", "x" })
	void failsOnOtherMailOrderValues(String raw) {
		assertThatThrownBy(() -> loadSingle("2110", "cane", "9", raw))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("line 2 [supplier_id 'SUP-X']")
			.hasMessageContaining("can_mail_order? '" + raw + "'");
	}

	// ---- service_zips ----

	@Test
	void parsesSingleZip() {
		ZipCoverage coverage = loadSingle("02110", "cane", "9", "y").serviceZips();

		assertThat(coverage.zips()).containsExactly(2110);
		assertThat(coverage.ranges()).isEmpty();
	}

	@Test
	void parsesListOfZips() {
		ZipCoverage coverage = loadSingle("02110, 02111,10001", "cane", "9", "y").serviceZips();

		assertThat(coverage.zips()).containsExactlyInAnyOrder(2110, 2111, 10001);
	}

	@Test
	void parsesRange() {
		ZipCoverage coverage = loadSingle("10001-10099", "cane", "9", "y").serviceZips();

		assertThat(coverage.zips()).isEmpty();
		assertThat(coverage.ranges()).containsExactly(new ZipRange(10001, 10099));
	}

	@Test
	void parsesListOfOverlappingRanges() {
		ZipCoverage coverage = loadSingle("2164-2213, 2143-2193, 2154-2171", "cane", "9", "y").serviceZips();

		assertThat(coverage.ranges()).containsExactly(new ZipRange(2164, 2213), new ZipRange(2143, 2193),
				new ZipRange(2154, 2171));
	}

	@Test
	void parsesMixedRangesAndSingles() {
		ZipCoverage coverage = loadSingle("10001-10099, 2110, 10050, 60601-60699", "cane", "9", "y").serviceZips();

		assertThat(coverage.zips()).containsExactlyInAnyOrder(2110, 10050);
		assertThat(coverage.ranges()).containsExactly(new ZipRange(10001, 10099), new ZipRange(60601, 60699));
	}

	@Test
	void treatsFourDigitZipAsLeadingZeroDropped() {
		ZipCoverage coverage = loadSingle("2110", "cane", "9", "y").serviceZips();

		assertThat(coverage.covers(2110)).isTrue();
		assertThat(coverage.covers(ZipCoverage.normalizeZip("02110").getAsInt())).isTrue();
	}

	@Test
	void treatsFourDigitRangeAsLeadingZeroDropped() {
		ZipCoverage coverage = loadSingle("2164-2213", "cane", "9", "y").serviceZips();

		assertThat(coverage.ranges()).containsExactly(new ZipRange(2164, 2213));
		assertThat(coverage.covers(ZipCoverage.normalizeZip("02170").getAsInt())).isTrue();
	}

	@Test
	void failsOnInvertedRange() {
		assertThatThrownBy(() -> loadSingle("2110, 10099-10001", "cane", "9", "y"))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("line 2 [supplier_id 'SUP-X']")
			.hasMessageContaining("inverted service_zips range '10099-10001'");
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "123456", "1-2-3", "02110-", "-02110", "021 10", "02110 - 02199", "1000a",
			"02110-123456" })
	void failsOnMalformedZipToken(String token) {
		assertThatThrownBy(() -> loadSingle("2110, " + token, "cane", "9", "y"))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("invalid service_zips value '" + token + "'");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", "2110,", ",2110", "2110,,2111" })
	void failsOnEmptyZipListOrToken(String serviceZips) {
		assertThatThrownBy(() -> loadSingle(serviceZips, "cane", "9", "y"))
			.isInstanceOf(SupplierDataException.class)
			.hasMessageContaining("service_zips")
			.hasMessageContaining("'" + serviceZips + "'");
	}

}

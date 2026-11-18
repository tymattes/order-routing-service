package com.tylermatteson.orderrouting.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ZipCoverageTests {

	@Test
	void rangeContainsItsBoundariesOnly() {
		ZipRange range = new ZipRange(2164, 2213);

		assertThat(range.contains(2163)).isFalse();
		assertThat(range.contains(2164)).isTrue();
		assertThat(range.contains(2213)).isTrue();
		assertThat(range.contains(2214)).isFalse();
	}

	@Test
	void singleZipRangeIsAllowed() {
		assertThat(new ZipRange(2110, 2110).contains(2110)).isTrue();
	}

	@Test
	void rangeRejectsInvertedBounds() {
		assertThatThrownBy(() -> new ZipRange(10099, 10001)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("start after end");
	}

	@ParameterizedTest
	@CsvSource({ "-1,100", "0,100000", "100000,100001" })
	void rangeRejectsValuesOutsideZipSpace(int start, int end) {
		assertThatThrownBy(() -> new ZipRange(start, end)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("outside 0..99999");
	}

	@Test
	void coversSinglesAndRangesAtBoundaries() {
		ZipCoverage coverage = new ZipCoverage(Set.of(2110, 60000),
				List.of(new ZipRange(10001, 10099), new ZipRange(100, 199)));

		assertThat(coverage.covers(2110)).isTrue();
		assertThat(coverage.covers(60000)).isTrue();
		assertThat(coverage.covers(2111)).isFalse();
		assertThat(coverage.covers(10000)).isFalse();
		assertThat(coverage.covers(10001)).isTrue();
		assertThat(coverage.covers(10099)).isTrue();
		assertThat(coverage.covers(10100)).isFalse();
		assertThat(coverage.covers(99)).isFalse();
		assertThat(coverage.covers(100)).isTrue();
		assertThat(coverage.covers(199)).isTrue();
		assertThat(coverage.covers(200)).isFalse();
	}

	@Test
	void fullCoverageRangeIsJustARange() {
		ZipCoverage coverage = new ZipCoverage(Set.of(), List.of(new ZipRange(100, 99999)));

		assertThat(coverage.covers(100)).isTrue();
		assertThat(coverage.covers(99999)).isTrue();
		assertThat(coverage.covers(99)).isFalse();
	}

	@Test
	void rejectsSingleZipOutsideZipSpace() {
		assertThatThrownBy(() -> new ZipCoverage(Set.of(100000), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void makesDefensiveImmutableCopies() {
		Set<Integer> zips = new HashSet<>(Set.of(2110));
		ZipCoverage coverage = new ZipCoverage(zips, List.of());
		zips.add(2111);

		assertThat(coverage.covers(2111)).isFalse();
		assertThatThrownBy(() -> coverage.zips().add(1)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> coverage.ranges().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "02110|2110", "2110|2110", "' 02110 '|2110", "02110-1234|2110",
			"2110-1234|2110", "00001|1", "1|1", "99999|99999", "00000|0" })
	void normalizesOrderZip(String raw, int expected) {
		assertThat(ZipCoverage.normalizeZip(raw)).isEqualTo(OptionalInt.of(expected));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  ", "abc", "123456", "02110-12", "02110-12345", "021 10", "-1", "02110-",
			"02110-02199", "1e4", "٠٢١١٠" })
	void returnsEmptyForInvalidOrderZip(String raw) {
		assertThat(ZipCoverage.normalizeZip(raw)).isEmpty();
	}

}

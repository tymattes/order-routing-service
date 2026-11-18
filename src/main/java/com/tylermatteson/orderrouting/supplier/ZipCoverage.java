package com.tylermatteson.orderrouting.supplier;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The zip codes a supplier serves locally: individual zips plus inclusive ranges. Zips are integers, so leading zeros
 * don't matter. Overlapping ranges and single zips inside a range are allowed and not merged.
 *
 * <p>Coverage of every zip (for example {@code 00100-99999}) is just a wide range; it is unrelated to whether the
 * supplier can mail order.
 *
 * @param zips   individual zips, each {@code 0..99999}
 * @param ranges zip ranges
 */
public record ZipCoverage(Set<Integer> zips, List<ZipRange> ranges) {

	static final int MIN_ZIP = 0;

	static final int MAX_ZIP = 99_999;

	/** 1-5 digits, optionally followed by a ZIP+4 suffix that is ignored. */
	private static final Pattern ORDER_ZIP = Pattern.compile("(\\d{1,5})(?:-\\d{4})?");

	public ZipCoverage {
		zips = Set.copyOf(zips);
		ranges = List.copyOf(ranges);
		for (int zip : zips) {
			if (zip < MIN_ZIP || zip > MAX_ZIP) {
				throw new IllegalArgumentException("zip " + zip + " is outside " + MIN_ZIP + ".." + MAX_ZIP);
			}
		}
	}

	public boolean covers(int zip) {
		if (zips.contains(zip)) {
			return true;
		}
		for (ZipRange range : ranges) {
			if (range.contains(zip)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Normalizes an incoming zip (for example from an order) so it can be compared with {@link #covers(int)}: trims,
	 * then accepts 1-5 ASCII digits, optionally followed by a ZIP+4 suffix ({@code 02110-1234}) which is dropped.
	 * {@code "2110"}, {@code "02110"} and {@code "02110-1234"} all give {@code 2110}.
	 *
	 * @return the zip as an int, or empty if {@code raw} is {@code null} or not a valid zip
	 */
	public static OptionalInt normalizeZip(String raw) {
		if (raw == null) {
			return OptionalInt.empty();
		}
		Matcher matcher = ORDER_ZIP.matcher(raw.trim());
		return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
	}

}

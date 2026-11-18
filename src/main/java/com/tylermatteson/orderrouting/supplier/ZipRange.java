package com.tylermatteson.orderrouting.supplier;

/**
 * An inclusive range of 5-digit zip codes held as integers, so leading zeros don't matter ({@code 2164} is
 * {@code 02164}).
 *
 * @param start first zip in the range, {@code 0..99999}
 * @param end   last zip in the range, {@code start..99999}
 */
public record ZipRange(int start, int end) {

	public ZipRange {
		if (start < ZipCoverage.MIN_ZIP || start > ZipCoverage.MAX_ZIP || end < ZipCoverage.MIN_ZIP
				|| end > ZipCoverage.MAX_ZIP) {
			throw new IllegalArgumentException("zip range " + start + "-" + end + " is outside "
					+ ZipCoverage.MIN_ZIP + ".." + ZipCoverage.MAX_ZIP);
		}
		if (start > end) {
			throw new IllegalArgumentException("zip range " + start + "-" + end + " has start after end");
		}
	}

	public boolean contains(int zip) {
		return zip >= start && zip <= end;
	}

}

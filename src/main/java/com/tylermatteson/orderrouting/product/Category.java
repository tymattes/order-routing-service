package com.tylermatteson.orderrouting.product;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The closed set of product categories. Each constant holds its normalized (trimmed, lower-case) label as it
 * appears in the data files.
 */
public enum Category {

	ANKLE_BRACE("ankle brace"),
	BACK_BRACE("back brace"),
	BLOOD_PRESSURE_MONITOR("blood pressure monitor"),
	CANE("cane"),
	CERVICAL_COLLAR("cervical collar"),
	COMMODE("commode"),
	COMPRESSION_STOCKINGS("compression stockings"),
	CPAP("cpap"),
	CPM_MACHINE("cpm machine"),
	CRUTCHES("crutches"),
	GLUCOSE_METER("glucose meter"),
	HEATING_PAD("heating pad"),
	HOSPITAL_BED("hospital bed"),
	ICE_MACHINE("ice machine"),
	KNEE_SCOOTER("knee scooter"),
	NEBULIZER("nebulizer"),
	OXYGEN("oxygen"),
	PATIENT_LIFT("patient lift"),
	ROLLATOR("rollator"),
	SHOWER_CHAIR("shower chair"),
	TENS_UNIT("tens unit"),
	TRACTION_DEVICE("traction device"),
	WALKER("walker"),
	WHEELCHAIR("wheelchair");

	private static final Map<String, Category> BY_LABEL = Arrays.stream(values())
		.collect(Collectors.toUnmodifiableMap(Category::label, Function.identity()));

	private final String label;

	Category(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/**
	 * Looks up a category by its exact normalized label. Callers are responsible for normalizing first; this
	 * method does not trim or change case.
	 */
	public static Optional<Category> fromLabel(String label) {
		return Optional.ofNullable(label).map(BY_LABEL::get);
	}

	/**
	 * Normalizes a raw value from a data file ({@code trim()} then {@code toLowerCase(Locale.ROOT)}) and looks it up
	 * with {@link #fromLabel}. Returns empty for {@code null} or an unrecognized value.
	 */
	public static Optional<Category> parse(String raw) {
		return raw == null ? Optional.empty() : fromLabel(raw.trim().toLowerCase(Locale.ROOT));
	}

}

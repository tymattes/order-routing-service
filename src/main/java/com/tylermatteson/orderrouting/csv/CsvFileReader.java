package com.tylermatteson.orderrouting.csv;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Does the generic work of loading a headed CSV reference-data file: checks the file exists and is readable,
 * checks the required columns are present, rejects rows with the wrong column count, and hands each remaining row
 * to the caller with its line number. Field rules and duplicate handling stay with each loader.
 *
 * <p>Every failure is raised through the {@link ExceptionFactory} so each data set keeps its own exception type.
 * Accepts LF and CRLF line endings; reads UTF-8.
 */
public final class CsvFileReader {

	/** Creates the loader-specific exception; {@code cause} may be {@code null}. */
	@FunctionalInterface
	public interface ExceptionFactory {

		DataLoadException create(String message, Throwable cause);

	}

	private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
		.setHeader()
		.setSkipHeaderRecord(true)
		.get();

	private final String dataName;

	private final List<String> requiredColumns;

	private final String idColumn;

	private final ExceptionFactory exceptionFactory;

	/**
	 * @param dataName         capitalized name used in messages, e.g. {@code "Product"}
	 * @param requiredColumns  header columns that must be present
	 * @param idColumn         the row-identifying column, included in row error messages; must be required
	 * @param exceptionFactory creates the exception thrown for every failure
	 */
	public CsvFileReader(String dataName, List<String> requiredColumns, String idColumn,
			ExceptionFactory exceptionFactory) {
		this.dataName = Objects.requireNonNull(dataName, "dataName");
		this.requiredColumns = List.copyOf(requiredColumns);
		this.idColumn = Objects.requireNonNull(idColumn, "idColumn");
		this.exceptionFactory = Objects.requireNonNull(exceptionFactory, "exceptionFactory");
		if (!this.requiredColumns.contains(idColumn)) {
			throw new IllegalArgumentException("idColumn '" + idColumn + "' must be one of " + requiredColumns);
		}
	}

	/**
	 * Reads {@code file}, calling {@code rowHandler} once per data row in file order. Exceptions thrown by the handler
	 * propagate unchanged.
	 */
	public void read(Path file, Consumer<CsvRow> rowHandler) {
		Path absolute = file.toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw fail(dataName + " data file not found: " + absolute + " (set DATA_DIR to the directory containing "
					+ absolute.getFileName() + ")", null);
		}
		if (!Files.isReadable(absolute)) {
			throw fail(dataName + " data file is not readable: " + absolute, null);
		}

		try (Reader reader = Files.newBufferedReader(absolute, StandardCharsets.UTF_8);
				CSVParser parser = FORMAT.parse(reader)) {
			List<String> headerNames = parser.getHeaderNames();
			requireColumns(headerNames, absolute);
			for (CSVRecord record : parser) {
				CsvRow row = new CsvRow(absolute, record.getRecordNumber() + 1, record, idColumn, exceptionFactory);
				if (!record.isConsistent()) {
					throw row.error("expected " + headerNames.size() + " columns but found " + record.size() + ": "
							+ record.toList());
				}
				rowHandler.accept(row);
			}
		}
		catch (IOException | UncheckedIOException ex) {
			throw fail("Failed to read " + dataName.toLowerCase(Locale.ROOT) + " data file " + absolute + ": "
					+ ex.getMessage(), ex);
		}
	}

	private void requireColumns(List<String> headerNames, Path file) {
		List<String> missing = requiredColumns.stream().filter(column -> !headerNames.contains(column)).toList();
		if (!missing.isEmpty()) {
			throw fail(dataName + " data file " + file + " is missing required column(s) " + missing
					+ "; found header " + headerNames, null);
		}
	}

	private DataLoadException fail(String message, Throwable cause) {
		return exceptionFactory.create(message, cause);
	}

}

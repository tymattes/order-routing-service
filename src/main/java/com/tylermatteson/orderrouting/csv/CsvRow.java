package com.tylermatteson.orderrouting.csv;

import java.nio.file.Path;

import org.apache.commons.csv.CSVRecord;

/**
 * One data row handed to a loader by {@link CsvFileReader}. Values are returned exactly as written in the file;
 * any trimming or normalization is up to the loader.
 */
public final class CsvRow {

	private final Path file;

	private final long line;

	private final CSVRecord record;

	private final String idColumn;

	private final CsvFileReader.ExceptionFactory exceptionFactory;

	CsvRow(Path file, long line, CSVRecord record, String idColumn, CsvFileReader.ExceptionFactory exceptionFactory) {
		this.file = file;
		this.line = line;
		this.record = record;
		this.idColumn = idColumn;
		this.exceptionFactory = exceptionFactory;
	}

	/** The absolute, normalized path of the file this row came from. */
	public Path file() {
		return file;
	}

	/** The 1-based file line of this row (the header is line 1), assuming one record per line. */
	public long line() {
		return line;
	}

	/** The raw value of {@code column}, untrimmed. */
	public String get(String column) {
		return record.get(column);
	}

	/**
	 * Builds a loader-specific exception for this row. The message is prefixed with the file, line number and, when
	 * present and non-blank, the row's id, e.g. {@code /data/suppliers.csv line 5 [supplier_id 'SUP-001']: detail}.
	 */
	public DataLoadException error(String detail) {
		StringBuilder message = new StringBuilder().append(file).append(" line ").append(line);
		if (record.isSet(idColumn) && !record.get(idColumn).isBlank()) {
			message.append(" [").append(idColumn).append(" '").append(record.get(idColumn)).append("']");
		}
		return exceptionFactory.create(message.append(": ").append(detail).toString(), null);
	}

}

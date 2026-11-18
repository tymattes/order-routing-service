package com.tylermatteson.orderrouting.csv;

/**
 * Base class for failures loading reference data at startup: the file is missing or unreadable, a required column
 * is absent, or a row is invalid. Each data set has its own subclass so callers can tell them apart.
 */
public abstract class DataLoadException extends RuntimeException {

	protected DataLoadException(String message) {
		super(message);
	}

	protected DataLoadException(String message, Throwable cause) {
		super(message, cause);
	}

}

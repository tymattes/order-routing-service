package com.tylermatteson.orderrouting.supplier;

import com.tylermatteson.orderrouting.csv.DataLoadException;

/**
 * Thrown when supplier data cannot be loaded: the file is missing or unreadable, a required column is absent,
 * or a row is invalid.
 */
public class SupplierDataException extends DataLoadException {

	public SupplierDataException(String message) {
		super(message);
	}

	public SupplierDataException(String message, Throwable cause) {
		super(message, cause);
	}

}

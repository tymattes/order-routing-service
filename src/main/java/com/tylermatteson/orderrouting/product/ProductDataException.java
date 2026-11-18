package com.tylermatteson.orderrouting.product;

import com.tylermatteson.orderrouting.csv.DataLoadException;

/**
 * Thrown when product data cannot be loaded: the file is missing or unreadable, a required column is absent,
 * or a row is invalid.
 */
public class ProductDataException extends DataLoadException {

	public ProductDataException(String message) {
		super(message);
	}

	public ProductDataException(String message, Throwable cause) {
		super(message, cause);
	}

}

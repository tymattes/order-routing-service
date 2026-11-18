package com.tylermatteson.orderrouting.route;

import java.util.List;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns request problems on {@link RouteController} into 200 failure responses ({@code feasible: false}), so the
 * endpoint always answers 200 and {@code feasible} carries the outcome. An unsupported {@code Content-Type} becomes
 * {@link RouteErrors#UNSUPPORTED_CONTENT_TYPE}; an unreadable body (malformed JSON, a wrong field type, or no body)
 * becomes {@link RouteErrors#MALFORMED_REQUEST}; a failed validation returns every error the validator collected.
 * Unexpected exceptions are deliberately not handled here and stay 500.
 */
@RestControllerAdvice(assignableTypes = RouteController.class)
class RouteExceptionHandler {

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	RouteResponse unsupportedContentType() {
		return RouteResponse.failure(List.of(RouteErrors.UNSUPPORTED_CONTENT_TYPE));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	RouteResponse malformedRequest() {
		return RouteResponse.failure(List.of(RouteErrors.MALFORMED_REQUEST));
	}

	@ExceptionHandler(RouteRequestValidator.ValidationException.class)
	RouteResponse invalidRequest(RouteRequestValidator.ValidationException ex) {
		return RouteResponse.failure(ex.errors());
	}

}

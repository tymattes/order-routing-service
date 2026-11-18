package com.tylermatteson.orderrouting.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.stereotype.Component;

import com.tylermatteson.orderrouting.product.Product;
import com.tylermatteson.orderrouting.product.ProductRepository;
import com.tylermatteson.orderrouting.supplier.ZipCoverage;

/**
 * Checks and normalizes a {@link RouteRequest}. It collects every error rather than stopping at the first, in this
 * order: {@code order_id}, {@code items}, {@code customer_zip}, {@code mail_order}, then line-item errors in request
 * order (for each line, product code before quantity).
 *
 * <p>Product codes are trimmed and upper-cased before lookup, and the validated order carries the normalized code.
 * If anything is wrong, {@link #validate} throws a {@link ValidationException} holding every error, which
 * {@link RouteExceptionHandler} returns as a 400.
 */
@Component
public class RouteRequestValidator {

	private final ProductRepository products;

	public RouteRequestValidator(ProductRepository products) {
		this.products = products;
	}

	/**
	 * @return the checked, normalized order
	 * @throws ValidationException with every error, in the documented order, if the request is invalid
	 */
	public ValidatedOrder validate(RouteRequest request) {
		List<String> errors = new ArrayList<>();

		boolean hasOrderId = request.orderId() != null && !request.orderId().isBlank();
		if (!hasOrderId) {
			errors.add(RouteErrors.MISSING_ORDER_ID);
		}

		List<RouteRequest.LineItem> items = request.items() == null ? List.of() : request.items();
		if (items.isEmpty()) {
			errors.add(RouteErrors.NO_LINE_ITEMS);
		}

		OptionalInt zip = ZipCoverage.normalizeZip(request.customerZip());
		if (zip.isEmpty()) {
			errors.add(RouteErrors.INVALID_CUSTOMER_ZIP);
		}

		if (request.mailOrder() == null) {
			errors.add(RouteErrors.MISSING_MAIL_ORDER);
		}

		List<ValidatedOrder.Line> lines = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			int lineNumber = i + 1;
			// A null element (`[null]`) is a line with neither field.
			RouteRequest.LineItem item = items.get(i) == null ? new RouteRequest.LineItem(null, null) : items.get(i);

			Optional<Product> product = Optional.empty();
			if (item.productCode() == null || item.productCode().isBlank()) {
				errors.add(RouteErrors.missingProductCode(lineNumber));
			}
			else {
				String code = normalizeProductCode(item.productCode());
				product = products.findByCode(code);
				if (product.isEmpty()) {
					errors.add(RouteErrors.unknownProductCode(code));
				}
			}

			boolean validQuantity = item.quantity() != null && item.quantity() >= 1;
			if (!validQuantity) {
				errors.add(RouteErrors.invalidQuantity(lineNumber));
			}

			if (product.isPresent() && validQuantity) {
				lines.add(new ValidatedOrder.Line(product.get(), item.quantity()));
			}
		}

		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
		return new ValidatedOrder(request.orderId().trim(), request.customerZip(), zip.getAsInt(),
				request.mailOrder(), lines);
	}

	/** Trim then upper-case, per README "Product and supplier codes". */
	static String normalizeProductCode(String raw) {
		return raw.trim().toUpperCase(Locale.ROOT);
	}

	/** A request failed validation. Carries every error, in the documented order (never empty). */
	public static final class ValidationException extends RuntimeException {

		private final List<String> errors;

		ValidationException(List<String> errors) {
			super(String.join(" ", errors), null, false, false);
			this.errors = List.copyOf(errors);
		}

		public List<String> errors() {
			return errors;
		}

	}

}

package com.tylermatteson.orderrouting.route;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/route}: validates an order, then routes it. Always returns 200; {@code feasible} tells the caller
 * the outcome. An infeasible order, an invalid or malformed request, and an unsupported {@code Content-Type} all come
 * back as {@code feasible: false} with {@code errors} (the request failures via {@link RouteExceptionHandler}). Only an
 * unexpected server error returns 500.
 *
 * <p>The mapping deliberately has no {@code consumes}: with it, Spring rejects a non-JSON {@code Content-Type} before
 * this controller is selected, so {@link RouteExceptionHandler} could not turn it into a 200.
 */
@RestController
public class RouteController {

	private final RouteRequestValidator validator;

	private final RoutingService routingService;

	public RouteController(RouteRequestValidator validator, RoutingService routingService) {
		this.validator = validator;
		this.routingService = routingService;
	}

	@PostMapping(path = "/api/route", produces = MediaType.APPLICATION_JSON_VALUE)
	public RouteResponse route(@RequestBody RouteRequest request) {
		return routingService.route(validator.validate(request));
	}

}

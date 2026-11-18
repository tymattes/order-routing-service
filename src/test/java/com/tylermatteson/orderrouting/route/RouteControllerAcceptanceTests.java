package com.tylermatteson.orderrouting.route;

import static com.tylermatteson.orderrouting.route.RouteErrors.INVALID_CUSTOMER_ZIP;
import static com.tylermatteson.orderrouting.route.RouteErrors.MALFORMED_REQUEST;
import static com.tylermatteson.orderrouting.route.RouteErrors.MISSING_MAIL_ORDER;
import static com.tylermatteson.orderrouting.route.RouteErrors.MISSING_ORDER_ID;
import static com.tylermatteson.orderrouting.route.RouteErrors.NO_LINE_ITEMS;
import static com.tylermatteson.orderrouting.route.RouteErrors.UNSUPPORTED_CONTENT_TYPE;
import static com.tylermatteson.orderrouting.route.RouteErrors.invalidQuantity;
import static com.tylermatteson.orderrouting.route.RouteErrors.missingProductCode;
import static com.tylermatteson.orderrouting.route.RouteErrors.noFeasibleSupplier;
import static com.tylermatteson.orderrouting.route.RouteErrors.unknownProductCode;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP-level acceptance tests for {@code POST /api/route}, against the real reference data in {@code data/}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RouteControllerAcceptanceTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Nested
	class Validation {

		@Test
		void emptyItemsIsRejected() throws Exception {
			Map<String, Object> order = validOrder();
			order.put("items", List.of());
			expectValidationErrors(order, NO_LINE_ITEMS);
		}

		@Test
		void missingItemsIsRejected() throws Exception {
			Map<String, Object> order = validOrder();
			order.remove("items");
			expectValidationErrors(order, NO_LINE_ITEMS);
		}

		@Test
		void missingCustomerZipIsRejected() throws Exception {
			Map<String, Object> order = validOrder();
			order.remove("customer_zip");
			expectValidationErrors(order, INVALID_CUSTOMER_ZIP);
		}

		@ParameterizedTest
		@ValueSource(strings = { "", "ABCDE", "123456" })
		void invalidCustomerZipIsRejected(String zip) throws Exception {
			Map<String, Object> order = validOrder();
			order.put("customer_zip", zip);
			expectValidationErrors(order, INVALID_CUSTOMER_ZIP);
		}

		@Test
		void emptyItemsAndMissingZipAreBothReportedInOrder() throws Exception {
			Map<String, Object> order = validOrder();
			order.put("items", List.of());
			order.remove("customer_zip");
			expectValidationErrors(order, NO_LINE_ITEMS, INVALID_CUSTOMER_ZIP);
		}

		@Test
		void missingOrderIdIsRejected() throws Exception {
			Map<String, Object> order = validOrder();
			order.remove("order_id");
			expectValidationErrors(order, MISSING_ORDER_ID);
		}

		@Test
		void blankOrderIdIsRejected() throws Exception {
			Map<String, Object> order = validOrder();
			order.put("order_id", "  ");
			expectValidationErrors(order, MISSING_ORDER_ID);
		}

		@Test
		void missingMailOrderIsRejected() throws Exception {
			Map<String, Object> order = validOrder();
			order.remove("mail_order");
			expectValidationErrors(order, MISSING_MAIL_ORDER);
		}

		@Test
		void blankProductCodeOnLineTwoIsRejected() throws Exception {
			Map<String, Object> order = validOrder();
			order.put("items", List.of(item("WC-STD-001", 1), item("  ", 1)));
			expectValidationErrors(order, missingProductCode(2));
		}

		@ParameterizedTest
		@NullSource
		@ValueSource(ints = { 0, -1 })
		void quantityBelowOneOrMissingIsRejected(Integer quantity) throws Exception {
			Map<String, Object> order = validOrder();
			order.put("items", List.of(item("WC-STD-001", quantity)));
			expectValidationErrors(order, invalidQuantity(1));
		}

		@Test
		void unknownProductCodeIsRejected() throws Exception {
			Map<String, Object> order = validOrder();
			order.put("items", List.of(item("XX-NOPE-999", 1)));
			expectValidationErrors(order, unknownProductCode("XX-NOPE-999"));
		}

		@Test
		void unparseableBodyIsMalformed() throws Exception {
			expectValidationErrors(postRaw("{not json"), MALFORMED_REQUEST);
		}

		@Test
		void wrongFieldTypeIsMalformed() throws Exception {
			String body = """
					{ "order_id": "ORD-X", "customer_zip": "10015", "mail_order": false,
					  "items": [ { "product_code": "WC-STD-001", "quantity": "abc" } ] }
					""";
			expectValidationErrors(postRaw(body), MALFORMED_REQUEST);
		}

		@Test
		void nonJsonContentTypeIsRejected() throws Exception {
			expectValidationErrors(mockMvc.perform(
					org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/route")
						.contentType(MediaType.TEXT_PLAIN)
						.content("{}")),
					UNSUPPORTED_CONTENT_TYPE);
		}

		@Test
		void missingContentTypeIsRejected() throws Exception {
			expectValidationErrors(mockMvc.perform(
					org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/route").content("{}")),
					UNSUPPORTED_CONTENT_TYPE);
		}

		@Test
		void everyErrorAtOnceIsReportedInDocumentedOrder() throws Exception {
			// NO_LINE_ITEMS can't coexist with line-item errors, so this covers every other validation error.
			Map<String, Object> order = new LinkedHashMap<>();
			order.put("customer_zip", "ABCDE");
			order.put("items", List.of(item(" ", 0), item("XX-NOPE-999", 2), item("WC-STD-001", null)));
			expectValidationErrors(order, MISSING_ORDER_ID, INVALID_CUSTOMER_ZIP, MISSING_MAIL_ORDER,
					missingProductCode(1), invalidQuantity(1), unknownProductCode("XX-NOPE-999"), invalidQuantity(3));
		}

	}

	@Nested
	class Routing {

		@Test
		void r1_noMailWithoutPermission() throws Exception {
			expectSingleShipment(post(order("00001", false, item("WC-STD-001", 1))), "SUP-T001", "local");
		}

		@Test
		void r2_mailCapableSupplierServingTheZipIsLocal() throws Exception {
			expectSingleShipment(post(order("00002", false, item("WC-STD-001", 1))), "SUP-T002", "local");
		}

		@Test
		void r3_tieBreakIsDeterministic() throws Exception {
			Map<String, Object> order = order("00003", false, item("CN-STD-063", 1));
			expectSingleShipment(post(order), "SUP-T003", "local");
			expectSingleShipment(post(order), "SUP-T003", "local");
		}

		@Test
		void r4_localBeatsMailAtEqualRating() throws Exception {
			expectSingleShipment(post(order("00005", true, item("WK-STD-009", 1))), "SUP-T005", "local");
		}

		@Test
		void r5_consolidationBeatsRatings() throws Exception {
			post(order("00007", false, item("WC-STD-001", 1), item("CN-STD-063", 1), item("WK-STD-009", 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feasible").value(true))
				.andExpect(jsonPath("$.errors").doesNotExist())
				.andExpect(jsonPath("$.routing.length()").value(1))
				.andExpect(jsonPath("$.routing[0].supplier_id").value("SUP-T007"))
				.andExpect(jsonPath("$.routing[0].items[*].product_code")
					.value(contains("WC-STD-001", "CN-STD-063", "WK-STD-009")));
		}

		@Test
		void r6_qualityOnASingleItem() throws Exception {
			expectSingleShipment(post(order("00007", false, item("WC-STD-001", 1))), "SUP-T008", "local");
		}

		@Test
		void r7_unratedSuppliersAreStillFeasible() throws Exception {
			expectSingleShipment(post(order("00040", false, item("RL-STD-058", 1))), "SUP-T011", "local");
		}

		@Test
		void r8_ratedBeatsUnrated() throws Exception {
			post(order("00040", true, item("RL-STD-058", 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feasible").value(true))
				.andExpect(jsonPath("$.routing.length()").value(1))
				.andExpect(jsonPath("$.routing[0].supplier_id").value(not(oneOf("SUP-T011", "SUP-T012"))))
				.andExpect(jsonPath("$.routing[0].items[0].fulfillment_mode").value("mail_order"));
		}

		@Test
		void r9_noCoverageIsInfeasible() throws Exception {
			expectInfeasible(post(order("00099", false, item("WC-STD-001", 1))),
					noFeasibleSupplier("WC-STD-001", "wheelchair", "00099"));
		}

		@Test
		void r10_onlyOptionWinsDespiteLowRating() throws Exception {
			expectSingleShipment(post(order("00098", false, item("WC-STD-001", 1))), "SUP-T014", "local");
		}

		@Test
		void r11_fullRangeCoverageIsNotMail() throws Exception {
			expectInfeasible(post(order("00050", false, item("CM-SENT-001", 1))),
					noFeasibleSupplier("CM-SENT-001", "cpm machine", "00050"));
		}

		@Test
		void r12_oneBadItemMakesTheOrderInfeasible() throws Exception {
			expectInfeasible(post(order("00001", false, item("WC-STD-001", 1), item("CN-STD-063", 1))),
					noFeasibleSupplier("CN-STD-063", "cane", "00001"));
		}

		@Test
		void everyInfeasibleLineIsReportedInRequestOrder() throws Exception {
			expectInfeasible(post(order("00099", false, item("WC-STD-001", 1), item("CN-STD-063", 1))),
					noFeasibleSupplier("WC-STD-001", "wheelchair", "00099"),
					noFeasibleSupplier("CN-STD-063", "cane", "00099"));
		}

		@Test
		void r13_zipAndProductCodeAreNormalized() throws Exception {
			post(order("00001-1234", false, item(" wc-std-001 ", 3)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feasible").value(true))
				.andExpect(jsonPath("$.routing.length()").value(1))
				.andExpect(jsonPath("$.routing[0].supplier_id").value("SUP-T001"))
				.andExpect(jsonPath("$.routing[0].items[0].product_code").value("WC-STD-001"))
				.andExpect(jsonPath("$.routing[0].items[0].quantity").value(3))
				.andExpect(jsonPath("$.routing[0].items[0].category").value("wheelchair"));
		}

	}

	@Nested
	class SampleOrders {

		@Test
		void ord001_highestRatedLocalSupplierCoveringBothItems() throws Exception {
			postRaw(sampleOrder("ORD-001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feasible").value(true))
				.andExpect(jsonPath("$.errors").doesNotExist())
				.andExpect(jsonPath("$.routing.length()").value(1))
				.andExpect(jsonPath("$.routing[0].supplier_id").value("SUP-0636"))
				.andExpect(jsonPath("$.routing[0].supplier_name").value("Care Supply Corp #636"))
				.andExpect(jsonPath("$.routing[0].items[*].product_code").value(contains("WC-STD-001", "OX-PORT-024")))
				.andExpect(jsonPath("$.routing[0].items[*].fulfillment_mode").value(everyItem(is("local"))));
		}

		@Test
		void ord002_singleLocalSupplierForAllFourItems() throws Exception {
			postRaw(sampleOrder("ORD-002"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feasible").value(true))
				.andExpect(jsonPath("$.routing.length()").value(1))
				.andExpect(jsonPath("$.routing[0].supplier_id").value("SUP-0928"))
				.andExpect(jsonPath("$.routing[0].supplier_name").value("Home Solutions Co #928"))
				.andExpect(jsonPath("$.routing[0].items[*].product_code")
					.value(contains("HB-FUL-018", "PL-ELEC-043", "CM-BED-048", "BP-AUTO-077")))
				.andExpect(jsonPath("$.routing[0].items[*].fulfillment_mode").value(everyItem(is("local"))));
		}

		@Test
		void ord003_mailBeatsLocalOutsideTolerance() throws Exception {
			postRaw(sampleOrder("ORD-003"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feasible").value(true))
				.andExpect(jsonPath("$.routing.length()").value(1))
				.andExpect(jsonPath("$.routing[0].supplier_id").value("SUP-023"))
				.andExpect(jsonPath("$.routing[0].supplier_name").value("Community Care Equipment"))
				.andExpect(jsonPath("$.routing[0].items[*].product_code")
					.value(contains("CP-STD-031", "CP-MSK-FF-035", "NB-COMP-039")))
				.andExpect(jsonPath("$.routing[0].items[1].quantity").value(2))
				.andExpect(jsonPath("$.routing[0].items[*].fulfillment_mode").value(everyItem(is("mail_order"))));
		}

	}

	// --- helpers ---

	/** A well-formed order; tests remove or replace fields to make it invalid. */
	private static Map<String, Object> validOrder() {
		return order("10015", false, item("WC-STD-001", 1));
	}

	@SafeVarargs
	private static Map<String, Object> order(String zip, boolean mailOrder, Map<String, Object>... items) {
		Map<String, Object> order = new LinkedHashMap<>();
		order.put("order_id", "ORD-TEST");
		order.put("customer_zip", zip);
		order.put("mail_order", mailOrder);
		order.put("items", new ArrayList<>(List.of(items)));
		return order;
	}

	/** A line item; a {@code null} quantity is left out of the JSON. */
	private static Map<String, Object> item(String productCode, Integer quantity) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("product_code", productCode);
		if (quantity != null) {
			item.put("quantity", quantity);
		}
		return item;
	}

	private static String sampleOrder(String orderId) throws Exception {
		JsonNode orders = JSON.readTree(Files.readString(Path.of("data/sample_orders.json")));
		for (JsonNode order : orders) {
			if (orderId.equals(order.get("order_id").asString())) {
				return JSON.writeValueAsString(order);
			}
		}
		throw new IllegalArgumentException("no sample order " + orderId);
	}

	private ResultActions post(Map<String, Object> order) throws Exception {
		return postRaw(JSON.writeValueAsString(order));
	}

	private ResultActions postRaw(String body) throws Exception {
		return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/route")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private void expectValidationErrors(Map<String, Object> order, String... errors) throws Exception {
		expectValidationErrors(post(order), errors);
	}

	private static void expectValidationErrors(ResultActions result, String... errors) throws Exception {
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.feasible").value(false))
			.andExpect(jsonPath("$.routing").doesNotExist())
			.andExpect(jsonPath("$.errors").value(contains(errors)));
	}

	private static void expectInfeasible(ResultActions result, String... errors) throws Exception {
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.feasible").value(false))
			.andExpect(jsonPath("$.routing").doesNotExist())
			.andExpect(jsonPath("$.errors").value(contains(errors)));
	}

	private static void expectSingleShipment(ResultActions result, String supplierId, String fulfillmentMode)
			throws Exception {
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.feasible").value(true))
			.andExpect(jsonPath("$.errors").doesNotExist())
			.andExpect(jsonPath("$.routing.length()").value(1))
			.andExpect(jsonPath("$.routing[0].supplier_id").value(supplierId))
			.andExpect(jsonPath("$.routing[0].items[*].fulfillment_mode").value(everyItem(is(fulfillmentMode))));
	}

}

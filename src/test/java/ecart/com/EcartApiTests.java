package ecart.com;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ecart.com.observability.RequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:ecart-api-tests?mode=memory&cache=shared")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class EcartApiTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void checkoutIsIdempotentForSameUserAndKey() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(RequestContext.CORRELATION_ID_HEADER, "test-cart")
                        .header(RequestContext.USER_ID_HEADER, "user-idempotent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-1","name":"Mug","unitPrice":1000,"quantity":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestContext.CORRELATION_ID_HEADER, "test-cart"))
                .andExpect(jsonPath("$.subtotal", is(2000)));

        String checkoutBody = "{}";
        String firstOrder = mockMvc.perform(post("/api/v1/checkout")
                        .header(RequestContext.CORRELATION_ID_HEADER, "test-checkout-1")
                        .header(RequestContext.USER_ID_HEADER, "user-idempotent")
                        .header(RequestContext.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestContext.IDEMPOTENCY_KEY_HEADER, "idem-1"))
                .andExpect(jsonPath("$.total", is(2000)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/api/v1/checkout")
                        .header(RequestContext.CORRELATION_ID_HEADER, "test-checkout-2")
                        .header(RequestContext.USER_ID_HEADER, "user-idempotent")
                        .header(RequestContext.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(extractOrderId(firstOrder))));
    }

    @Test
    void checkoutRejectsIdempotencyKeyReuseWithDifferentBody() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(RequestContext.USER_ID_HEADER, "user-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-2","name":"Pen","unitPrice":200,"quantity":1}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/checkout")
                        .header(RequestContext.USER_ID_HEADER, "user-conflict")
                        .header(RequestContext.IDEMPOTENCY_KEY_HEADER, "idem-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/checkout")
                        .header(RequestContext.USER_ID_HEADER, "user-conflict")
                        .header(RequestContext.IDEMPOTENCY_KEY_HEADER, "idem-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"discountCode":"SAVE10"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    void adminCanGenerateAndReportDiscountCodeUsage() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(RequestContext.USER_ID_HEADER, "user-discount-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-3","name":"Book","unitPrice":1000,"quantity":1}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/checkout")
                        .header(RequestContext.USER_ID_HEADER, "user-discount-a")
                        .header(RequestContext.IDEMPOTENCY_KEY_HEADER, "idem-discount-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/discount-codes")
                        .header(RequestContext.ADMIN_ID_HEADER, "admin-1")
                        .header(RequestContext.ADMIN_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nthOrder":1,"discountPercent":10,"expiresInDays":30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(RequestContext.USER_ID_HEADER, "user-discount-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-4","name":"Bag","unitPrice":1000,"quantity":1}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/checkout")
                        .header(RequestContext.USER_ID_HEADER, "user-discount-b")
                        .header(RequestContext.IDEMPOTENCY_KEY_HEADER, "idem-discount-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"discountCode":"SAVE10-000001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountAmount", is(100)))
                .andExpect(jsonPath("$.total", is(900)));

        mockMvc.perform(get("/api/v1/admin/reports/summary")
                        .header(RequestContext.ADMIN_ID_HEADER, "admin-1")
                        .header(RequestContext.ADMIN_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCodes[0].status", is("USED")))
                .andExpect(jsonPath("$.totalDiscountGiven", is(100)));
    }

    @Test
    void discountCodeGenerationUsesEveryNthOrderNotOnlyCurrentOrderCount() throws Exception {
        placeSimpleOrder("nth-user-1", "nth-idem-1");
        placeSimpleOrder("nth-user-2", "nth-idem-2");
        placeSimpleOrder("nth-user-3", "nth-idem-3");
        placeSimpleOrder("nth-user-4", "nth-idem-4");

        mockMvc.perform(post("/api/v1/admin/discount-codes")
                        .header(RequestContext.ADMIN_ID_HEADER, "admin-nth")
                        .header(RequestContext.ADMIN_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nthOrder":3,"discountPercent":15,"expiresInDays":30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("SAVE15-000003")))
                .andExpect(jsonPath("$.triggeredByOrderNumber", is(3)));
    }

    @Test
    void openApiDocsArePublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title", is("Ecart Backend API")));
    }

    private void placeSimpleOrder(String userId, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(RequestContext.USER_ID_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-NTH","name":"Nth Item","unitPrice":100,"quantity":1}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/checkout")
                        .header(RequestContext.USER_ID_HEADER, userId)
                        .header(RequestContext.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    private String extractOrderId(String json) {
        int start = json.indexOf("\"orderId\":\"") + 11;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}


package com.cbms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankSchemeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(
            String role
    ) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject("scheme-test-user")
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private long createProduct(String code) throws Exception {
        String body = """
                {
                  "prodCd": "%s",
                  "prodNm": "Test Hire Purchase",
                  "prodCat": "HIRE_PURCHASE",
                  "bankingTyp": "CONVENTIONAL",
                  "ccyCd": "MYR",
                  "prodDesc": "Scheme integration test"
                }
                """.formatted(code);

        String response = mockMvc.perform(
                        post("/api/products")
                                .with(jwt("ROLE_MAKER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response)
                .get("prodId").asLong();
    }

    private long createScheme(long prodId, String code) throws Exception {
        String body = """
                {
                  "prodId": %d,
                  "schmCd": "%s",
                  "schmNm": "New Vehicle Scheme",
                  "schmDesc": "New vehicle financing",
                  "minAmt": 10000.00,
                  "maxAmt": 300000.00,
                  "minTenor": 12,
                  "maxTenor": 108,
                  "eligibilityDesc": "Approved individual customers"
                }
                """.formatted(prodId, code);

        String response = mockMvc.perform(
                        post("/api/schemes")
                                .with(jwt("ROLE_MAKER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response)
                .get("schmId").asLong();
    }

    @Test
    void schemeLifecycleShouldMaintainAuditHistory() throws Exception {

        String unique = UUID.randomUUID()
                .toString().replace("-", "")
                .substring(0, 12).toUpperCase();

        long prodId = createProduct("HP" + unique);
        long schmId = createScheme(prodId, "NV" + unique);

        // Scheme begins in DRAFT.
        String initialStatus = jdbcTemplate.queryForObject(
                "SELECT schm_sts FROM public.bank_scheme WHERE schm_id = ?",
                String.class,
                schmId
        );

        assertThat(initialStatus).isEqualTo("DRAFT");

        // Update while DRAFT.
        mockMvc.perform(
                put("/api/schemes/{schmId}", schmId)
                        .with(jwt("ROLE_MAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schmNm": "Updated Vehicle Scheme",
                                  "schmDesc": "Updated financing scheme",
                                  "minAmt": 15000.00,
                                  "maxAmt": 250000.00,
                                  "minTenor": 12,
                                  "maxTenor": 96,
                                  "eligibilityDesc": "Customers with completed KYC"
                                }
                                """)
        ).andExpect(status().isOk());

        // Cannot activate while parent product is DRAFT.
        mockMvc.perform(
                patch("/api/schemes/{schmId}/status", schmId)
                        .with(jwt("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
        ).andExpect(status().isConflict());

        // Activate parent product first.
        mockMvc.perform(
                patch("/api/products/{prodId}/status", prodId)
                        .with(jwt("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
        ).andExpect(status().isOk());

        // Then activate scheme.
        mockMvc.perform(
                patch("/api/schemes/{schmId}/status", schmId)
                        .with(jwt("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
        ).andExpect(status().isOk());

        // ACTIVE schemes cannot be edited.
        mockMvc.perform(
                put("/api/schemes/{schmId}", schmId)
                        .with(jwt("ROLE_MAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schmNm": "Should Not Update",
                                  "minAmt": 100.00,
                                  "maxAmt": 200.00
                                }
                                """)
        ).andExpect(status().isConflict());

        // MAKER cannot activate/deactivate.
        mockMvc.perform(
                patch("/api/schemes/{schmId}/status", schmId)
                        .with(jwt("ROLE_MAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}")
        ).andExpect(status().isForbidden());

        // Audit history: CREATE, UPDATE, STATUS_CHANGE.
        List<String> actions = jdbcTemplate.queryForList(
                """
                        SELECT action_typ
                        FROM public.bank_scheme_audit
                        WHERE schm_id = ?
                        ORDER BY audit_id
                        """,
                String.class,
                schmId
        );

        assertThat(actions).containsExactly(
                "CREATE",
                "UPDATE",
                "STATUS_CHANGE"
        );

        // Failed update must not change scheme name.
        String schemeName = jdbcTemplate.queryForObject(
                "SELECT schm_nm FROM public.bank_scheme WHERE schm_id = ?",
                String.class,
                schmId
        );

        assertThat(schemeName).isEqualTo("Updated Vehicle Scheme");
    }

    @Test
    void invalidAmountRangeShouldBeRejected() throws Exception {

        String unique = UUID.randomUUID()
                .toString().replace("-", "")
                .substring(0, 12).toUpperCase();

        long prodId = createProduct("HP" + unique);

        String body = """
                {
                  "prodId": %d,
                  "schmCd": "BAD%s",
                  "schmNm": "Invalid Scheme",
                  "minAmt": 300000.00,
                  "maxAmt": 10000.00,
                  "minTenor": 12,
                  "maxTenor": 108
                }
                """.formatted(prodId, unique);

        mockMvc.perform(
                post("/api/schemes")
                        .with(jwt("ROLE_MAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateSchemeCodeShouldBeRejected() throws Exception {

        String unique = UUID.randomUUID()
                .toString().replace("-", "")
                .substring(0, 12).toUpperCase();

        long prodId = createProduct("HP" + unique);

        createScheme(prodId, "NV" + unique);

        String duplicateBody = """
                {
                  "prodId": %d,
                  "schmCd": "NV%s",
                  "schmNm": "Duplicate Scheme",
                  "minAmt": 10000.00,
                  "maxAmt": 300000.00
                }
                """.formatted(prodId, unique);

        mockMvc.perform(
                post("/api/schemes")
                        .with(jwt("ROLE_MAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateBody)
        ).andExpect(status().isConflict());
    }
}

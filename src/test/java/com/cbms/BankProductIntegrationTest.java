
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
class BankProductIntegrationTest {

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
                .jwt(token -> token.subject("product-test-user")
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    @Test
    void productLifecycleShouldCreateAuditHistory() throws Exception {

        String code = "T" + UUID.randomUUID()
                .toString().replace("-", "")
                .substring(0, 12).toUpperCase();

        String createBody = """
                {
                  "prodCd": "%s",
                  "prodNm": "Test Savings Product",
                  "prodCat": "SAVINGS",
                  "bankingTyp": "CONVENTIONAL",
                  "ccyCd": "MYR",
                  "prodDesc": "Integration test"
                }
                """.formatted(code);

        String createResponse = mockMvc.perform(
                        post("/api/products")
                                .with(jwt("ROLE_MAKER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        long prodId = created.get("prodId").asLong();

        assertThat(created.get("prodSts").asText()).isEqualTo("DRAFT");

        mockMvc.perform(
                put("/api/products/{prodId}", prodId)
                        .with(jwt("ROLE_MAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prodNm": "Updated Savings Product",
                                  "prodDesc": "Updated description"
                                }
                                """)
        ).andExpect(status().isOk());

        mockMvc.perform(
                patch("/api/products/{prodId}/status", prodId)
                        .with(jwt("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
        ).andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_product_audit
                        WHERE prod_id = ?
                        """,
                Integer.class,
                prodId
        );

        assertThat(auditCount).isEqualTo(3);

        List<String> actions = jdbcTemplate.queryForList(
                """
                        SELECT action_typ
                        FROM public.bank_product_audit
                        WHERE prod_id = ?
                        ORDER BY audit_id
                        """,
                String.class,
                prodId
        );

        assertThat(actions).containsExactly(
                "CREATE",
                "UPDATE",
                "STATUS_CHANGE"
        );

        mockMvc.perform(
                put("/api/products/{prodId}", prodId)
                        .with(jwt("ROLE_MAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prodNm": "Should Not Update",
                                  "prodDesc": "Blocked"
                                }
                                """)
        ).andExpect(status().isConflict());

        String productName = jdbcTemplate.queryForObject(
                """
                        SELECT prod_nm
                        FROM public.bank_product
                        WHERE prod_id = ?
                        """,
                String.class,
                prodId
        );

        assertThat(productName).isEqualTo("Updated Savings Product");
    }
}

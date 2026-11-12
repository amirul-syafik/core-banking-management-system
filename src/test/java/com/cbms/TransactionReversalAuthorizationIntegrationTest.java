package com.cbms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prevent HTTP clients from bypassing the maker-checker reversal workflow.
 * BankTransactionReversalService remains callable internally by the approval service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionReversalAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void adminCannotPerformDirectReversal() throws Exception {
        mvc.perform(post("/api/transactions/123/reversals")
                        .with(user("admin-test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "direct-reversal-admin-test")
                        .content("{\"reason\":\"Bypass attempt\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void makerCannotPerformDirectReversal() throws Exception {
        mvc.perform(post("/api/transactions/123/reversals")
                        .with(user("maker-test").roles("MAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "direct-reversal-maker-test")
                        .content("{\"reason\":\"Bypass attempt\"}"))
                .andExpect(status().isForbidden());
    }
}

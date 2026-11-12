package com.cbms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ensures legacy direct FD posting endpoints cannot bypass the maker-checker flow.
 * Tests exercise the actual Spring SecurityFilterChain through MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankFixedDepositApprovalHttpSecurityIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void adminCannotPostMaturitySettlementDirectly() throws Exception {
        mvc.perform(post("/api/fixed-deposits/123/maturity/settle")
                .with(user("admin-test").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinationAccountId\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotPostRenewalDirectly() throws Exception {
        mvc.perform(post("/api/fixed-deposits/123/renew")
                .with(user("admin-test").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"renewalType\":\"PRINCIPAL_ONLY\",\"schemeId\":1,\"tenorMonths\":3}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotPostPrematureUpliftDirectly() throws Exception {
        mvc.perform(post("/api/fixed-deposits/123/premature-withdraw")
                .with(user("admin-test").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinationAccountId\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotSubmitApprovalRequest() throws Exception {
        mvc.perform(post("/api/fd-approval-requests")
                .with(user("viewer-test").roles("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    void makerCanReachSubmissionValidation() throws Exception {
        mvc.perform(post("/api/fd-approval-requests")
                .with(user("maker-test").roles("MAKER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void makerCannotApproveAnotherMakerRequest() throws Exception {
        mvc.perform(post("/api/fd-approval-requests/999999/approve")
                .with(user("maker-test").roles("MAKER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void makerCannotRejectAnotherMakerRequest() throws Exception {
        mvc.perform(post("/api/fd-approval-requests/999999/reject")
                .with(user("maker-test").roles("MAKER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No supporting documents\"}"))
            .andExpect(status().isForbidden());
    }

    private static String submitBody() {
        return "{\"fdId\":123,\"actionType\":\"RENEWAL\",\"payload\":{\"renewalType\":\"PRINCIPAL_ONLY\",\"schemeId\":1,\"tenorMonths\":3},\"reason\":\"Review requested\"}";
    }
}

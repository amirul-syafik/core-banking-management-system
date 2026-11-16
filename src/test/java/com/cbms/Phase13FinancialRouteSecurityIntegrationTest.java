package com.cbms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13 filter-chain regression: direct financial mutations and bypass routes
 * must remain forbidden even for ADMIN. These tests do not modify bank data.
 * Execute with the project's real Maven profile/database and Spring test stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase13FinancialRouteSecurityIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void noOneCanInvokeDirectIslamicFinancialExecution() throws Exception {
        for (String route : new String[] {
                "/api/islamic/deposits/1/execute",
                "/api/islamic/deposits/1/activate",
                "/api/islamic/deposits/1/post",
                "/api/islamic/deposits/1/settle",
                "/api/islamic/deposits/1/reverse",
                "/api/islamic/deposits/1/phase10-gates"
        }) {
            mvc.perform(post(route).with(user("regression-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCannotAttestOrDeleteIslamicPoliciesThroughApplication() throws Exception {
        mvc.perform(patch("/api/islamic/execution-policies/1/attest")
                .with(user("regression-admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/islamic/execution-policies/1")
                .with(user("regression-admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void directReversalAndFixedDepositSettlementStayDeniedEvenForAdmin() throws Exception {
        for (String route : new String[] {
                "/api/transactions/1/reversals",
                "/api/fixed-deposits/1/renew",
                "/api/fixed-deposits/1/premature-withdraw",
                "/api/fixed-deposits/1/maturity/settle",
                "/api/financing/facilities"
        }) {
            mvc.perform(post(route).with(user("regression-admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void viewerCannotWriteFinancialRequestsOrApprovePolicy() throws Exception {
        for (String route : new String[] {
                "/api/accounts/1/deposits",
                "/api/accounts/1/withdrawals",
                "/api/accounts/1/transfers",
                "/api/islamic/execution-policies",
                "/api/islamic/execution-policies/1/decision"
        }) {
            mvc.perform(post(route).with(user("regression-viewer").roles("VIEWER"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void makerCannotApproveHighRiskDecisions() throws Exception {
        for (String route : new String[] {
                "/api/fd-approval-requests/1/approve",
                "/api/financing/applications/1/approve",
                "/api/islamic/execution-policies/1/decision",
                "/api/islamic/deposits/execution-timeline/1/decision"
        }) {
            mvc.perform(post(route).with(user("regression-maker").roles("MAKER"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }
}

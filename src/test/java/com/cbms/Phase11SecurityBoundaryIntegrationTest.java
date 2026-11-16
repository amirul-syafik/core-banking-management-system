package com.cbms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 11 HTTP authorization smoke tests for the actual SecurityFilterChain.
 * Only requests denied by the filter chain are exercised; no bank mutations are
 * performed and no financial or Shariah policy fixtures are required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase11SecurityBoundaryIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void adminCannotReachUnimplementedIslamicFinancialMutationRoutes() throws Exception {
        for (String route : new String[] {
                "/api/islamic/deposits/1/execute",
                "/api/islamic/deposits/1/activate",
                "/api/islamic/deposits/1/post",
                "/api/islamic/deposits/1/settle",
                "/api/islamic/deposits/1/reverse"
        }) {
            mvc.perform(post(route)
                    .with(user("phase11-admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void viewerCannotCreateIslamicPolicyOrFinancialTransactions() throws Exception {
        for (String route : new String[] {
                "/api/islamic/execution-policies",
                "/api/islamic/deposits/1/execution-timeline",
                "/api/accounts/1/deposits",
                "/api/accounts/1/transfers"
        }) {
            mvc.perform(post(route)
                    .with(user("phase11-viewer").roles("VIEWER"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void makerCannotSelfAuthorizePolicyOrFinancialApprovals() throws Exception {
        for (String route : new String[] {
                "/api/islamic/execution-policies/1/decision",
                "/api/islamic/deposits/execution-timeline/1/decision",
                "/api/fd-approval-requests/1/approve"
        }) {
            mvc.perform(post(route)
                    .with(user("phase11-maker").roles("MAKER"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void noDirectFacilityBookingOrReversalBypassEvenForAdmin() throws Exception {
        for (String route : new String[] {
                "/api/financing/facilities",
                "/api/transactions/1/reversals",
                "/api/fixed-deposits/1/maturity/settle"
        }) {
            mvc.perform(post(route)
                    .with(user("phase11-admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void viewerCannotOpenAdminReconciliationExceptions() throws Exception {
        mvc.perform(get("/api/reconciliation/exceptions")
                .with(user("phase11-viewer").roles("VIEWER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestCannotReadIslamicReleaseControls() throws Exception {
        mvc.perform(get("/api/islamic/deposits/1/phase10-gates"))
            .andExpect(status().isUnauthorized());
    }
}

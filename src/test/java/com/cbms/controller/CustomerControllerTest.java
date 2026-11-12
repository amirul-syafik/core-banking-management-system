package com.cbms.controller;

import com.cbms.service.CustomerService;
import com.cbms.service.ApplicationActivityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import(com.cbms.exception.GlobalExceptionHandler.class)
@WithMockUser(username = "test-maker", roles = "MAKER")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    // MVC audit interceptor is part of this slice; mock its persistence dependency.
    @MockitoBean
    private ApplicationActivityAuditService applicationActivityAuditService;

    @Test
    void createCustomerWithMissingNameReturnsBadRequest()
            throws Exception {

        String requestBody = """
                {
                    "custTyp": "INDIVIDUAL"
                }
                """;

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));
    }

    @Test
    void createCustomerWithInvalidTypeReturnsBadRequest()
            throws Exception {

        String requestBody = """
                {
                    "custNm": "Test Customer",
                    "custTyp": "INVALID"
                }
                """;

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));
    }

    @Test
    void createCustomerWithMalformedJsonReturnsBadRequest()
            throws Exception {

        String requestBody = """
                {
                    "custNm": "Test Customer",
                    "custTyp":
                """;

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Invalid request body or data format"));
    }
}

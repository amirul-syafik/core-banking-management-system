package com.cbms.controller;

import com.cbms.service.CustomerService;
import org.junit.jupiter.api.Test;
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
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

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

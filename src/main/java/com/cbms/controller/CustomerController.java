package com.cbms.controller;

import com.cbms.dto.CustomerCreateRequest;
import com.cbms.entity.Customer;
import com.cbms.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer createCustomer(
            @Valid @RequestBody CustomerCreateRequest request) {
        return customerService.createCustomer(request);
    }

    @GetMapping("/{custId}")
    public Customer getCustomer(@PathVariable Long custId) {
        return customerService.getCustomer(custId);
    }

    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }
}

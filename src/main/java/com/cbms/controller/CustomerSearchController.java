package com.cbms.controller;

import com.cbms.entity.Customer;
import com.cbms.service.CustomerSearchService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerSearchController {

    private final CustomerSearchService customerSearchService;

    public CustomerSearchController(
            CustomerSearchService customerSearchService) {
        this.customerSearchService = customerSearchService;
    }

    @GetMapping("/search")
    public Page<Customer> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return customerSearchService.search(
                name, type, status, page, size);
    }
}

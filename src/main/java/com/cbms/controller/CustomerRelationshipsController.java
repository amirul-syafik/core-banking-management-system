
package com.cbms.controller;

import com.cbms.dto.CustomerRelationshipsResponse;
import com.cbms.service.CustomerRelationshipsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerRelationshipsController {

    private final CustomerRelationshipsService service;

    public CustomerRelationshipsController(CustomerRelationshipsService service) {
        this.service = service;
    }

    @GetMapping("/{custId}/portfolio")
    public CustomerRelationshipsResponse get(@PathVariable Long custId) {
        return service.get(custId);
    }
}

package com.cbms.controller;

import com.cbms.dto.CustomerContactRequest;
import com.cbms.entity.CustomerContact;
import com.cbms.service.CustomerContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/contacts")
public class CustomerContactController {

    private final CustomerContactService contactService;

    public CustomerContactController(
            CustomerContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerContact addContact(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerContactRequest request) {

        return contactService.addContact(custId, request);
    }

    @GetMapping
    public List<CustomerContact> getContacts(
            @PathVariable Long custId) {

        return contactService.getContacts(custId);
    }
}
package com.cbms.controller;

import com.cbms.reference.ReferenceCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated, read-only ISO options for customer-profile reference fields. */
@RestController
@RequestMapping("/api/reference-data")
public class ReferenceDataController {
    @GetMapping("/countries")
    public List<ReferenceCatalog.Option> countries() { return ReferenceCatalog.countries(); }

    @GetMapping("/currencies")
    public List<ReferenceCatalog.Option> currencies() { return ReferenceCatalog.currencies(); }
}

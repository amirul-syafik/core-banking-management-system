package com.cbms.controller;

import com.cbms.dto.BankBaseRateCreateRequest;
import com.cbms.dto.BankSchemeRateCreateRequest;
import com.cbms.entity.BankBaseRate;
import com.cbms.entity.BankSchemeRate;
import com.cbms.service.BankRateService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rates")
public class BankRateController {

    private final BankRateService service;

    public BankRateController(BankRateService service) {
        this.service = service;
    }

    @PostMapping("/base")
    @ResponseStatus(HttpStatus.CREATED)
    public BankBaseRate createBaseRate(
            @Valid @RequestBody BankBaseRateCreateRequest request,
            Authentication authentication
    ) {
        return service.createBaseRate(request, authentication);
    }

    @PostMapping("/scheme")
    @ResponseStatus(HttpStatus.CREATED)
    public BankSchemeRate createSchemeRate(
            @Valid @RequestBody BankSchemeRateCreateRequest request,
            Authentication authentication
    ) {
        return service.createSchemeRate(request, authentication);
    }

    @GetMapping("/base/{rateCd}/history")
    public List<BankBaseRate> baseRateHistory(
            @PathVariable String rateCd
    ) {
        return service.baseRateHistory(rateCd);
    }

    @GetMapping("/scheme/{schmId}/history")
    public List<BankSchemeRate> schemeRateHistory(
            @PathVariable Long schmId
    ) {
        return service.schemeRateHistory(schmId);
    }

    @GetMapping("/scheme/{schmId}/effective")
    public Map<String, Object> effectiveRate(
            @PathVariable Long schmId,
            @RequestParam LocalDate date
    ) {
        BigDecimal rate = service.resolveRate(schmId, date);

        return Map.of(
                "schmId", schmId,
                "effectiveDate", date,
                "effectiveRatePct", rate
        );
    }
}
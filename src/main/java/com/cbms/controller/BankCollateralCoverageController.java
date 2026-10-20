package com.cbms.controller;

import com.cbms.dto.BankCollateralCoverageResponse;
import com.cbms.dto.BankCollateralExposureResponse;
import com.cbms.service.BankCollateralCoverageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/collateral-monitoring")
public class BankCollateralCoverageController {
    private final BankCollateralCoverageService service;

    public BankCollateralCoverageController(BankCollateralCoverageService service) {
        this.service = service;
    }

    @GetMapping("/facilities/{facId}/coverage")
    public BankCollateralCoverageResponse facilityCoverage(@PathVariable Long facId) {
        return service.facilityCoverage(facId);
    }

    @GetMapping("/collaterals/{collateralId}/exposure")
    public BankCollateralExposureResponse collateralExposure(@PathVariable Long collateralId) {
        return service.collateralExposure(collateralId);
    }
}

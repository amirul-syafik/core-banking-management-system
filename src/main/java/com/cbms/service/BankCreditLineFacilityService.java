package com.cbms.service;

import com.cbms.dto.BankCreditLineFacilityCreateRequest;
import com.cbms.entity.BankCreditLineFacility;
import com.cbms.entity.BankProduct;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankCreditLineFacilityRepository;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BankCreditLineFacilityService {

    private final BankCreditLineFacilityRepository facilityRepository;
    private final BankProductRepository productRepository;
    private final BankSchemeRepository schemeRepository;
    private final JdbcTemplate jdbc;

    public BankCreditLineFacilityService(
            BankCreditLineFacilityRepository facilityRepository,
            BankProductRepository productRepository,
            BankSchemeRepository schemeRepository,
            JdbcTemplate jdbc
    ) {
        this.facilityRepository = facilityRepository;
        this.productRepository = productRepository;
        this.schemeRepository = schemeRepository;
        this.jdbc = jdbc;
    }

    @Transactional
    public BankCreditLineFacility create(
            BankCreditLineFacilityCreateRequest request,
            Authentication authentication
    ) {
        if (!List.of("REVOLVING_CREDIT", "FLOOR_STOCKING")
                .contains(request.clFacTyp())) {
            throw badRequest("Invalid credit line facility type");
        }

        if (!List.of("FIXED", "FLOATING").contains(request.rateTyp())) {
            throw badRequest("Invalid rate type");
        }

        if (request.effDt() == null
                || request.expiryDt() == null
                || !request.expiryDt().isAfter(request.effDt())) {
            throw badRequest("Expiry date must be after effective date");
        }

        if (request.apprLimit() == null
                || request.apprLimit().signum() <= 0
                || request.apprLimit().scale() > 2) {
            throw badRequest("Invalid approved limit");
        }

        if (request.effRate() == null
                || request.effRate().signum() < 0
                || request.effRate().scale() > 6) {
            throw badRequest("Invalid effective rate");
        }

        Integer validCustomer = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.customer_master
                WHERE cust_id = ?
                """,
                Integer.class,
                request.custId()
        );

        if (validCustomer == null || validCustomer == 0) {
            throw badRequest("Customer does not exist");
        }

        BankProduct product = productRepository
                .findById(request.prodId())
                .orElseThrow(() -> badRequest("Product does not exist"));

        BankScheme scheme = schemeRepository
                .findById(request.schmId())
                .orElseThrow(() -> badRequest("Scheme does not exist"));

        if (!"ACTIVE".equals(product.getProdSts())) {
            throw badRequest("Product must be ACTIVE");
        }

        if (!"ACTIVE".equals(scheme.getSchmSts())) {
            throw badRequest("Scheme must be ACTIVE");
        }

        if (!product.getProdId().equals(scheme.getProdId())) {
            throw badRequest("Scheme does not belong to product");
        }

        if (!"MYR".equals(product.getCcyCd())) {
            throw badRequest("Only MYR credit lines are supported");
        }

        // A revolving credit facility must use a credit-line product.
        // Floor stocking is treated as a distinct product category.
        String expectedCategory =
                "REVOLVING_CREDIT".equals(request.clFacTyp())
                        ? "CREDIT_LINE"
                        : "FLOOR_STOCKING";

        if (!expectedCategory.equals(product.getProdCat())) {
            throw badRequest(
                    "Product category must be " + expectedCategory
            );
        }

        // For this initial implementation, require a conventional
        // banking product. Islamic financing needs separate contracts
        // and profit-calculation rules.
        if (!"CONVENTIONAL".equals(product.getBankingTyp())) {
            throw badRequest(
                    "Supports CONVENTIONAL credit lines only"
            );
        }

        if (scheme.getMinAmt() != null
                && request.apprLimit().compareTo(scheme.getMinAmt()) < 0) {
            throw badRequest("Approved limit is below scheme minimum");
        }

        if (scheme.getMaxAmt() != null
                && request.apprLimit().compareTo(scheme.getMaxAmt()) > 0) {
            throw badRequest("Approved limit exceeds scheme maximum");
        }

        // Verify the requested rate matches an effective scheme rate.
        // This prevents arbitrary rates being entered on facility setup.
        Integer validRate = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_scheme_rate
                WHERE schm_id = ?
                  AND rate_typ = ?
                  AND rate_sts = 'ACTIVE'
                  AND eff_dt <= ?
                  AND (
                      (? = 'FIXED' AND fixed_rate_pct = ?)
                      OR
                      (? = 'FLOATING' AND (
                          COALESCE((
                              SELECT br.rate_pct
                              FROM public.bank_base_rate br
                              WHERE br.rate_cd =
                                    bank_scheme_rate.base_rate_cd
                                AND br.rate_sts = 'ACTIVE'
                                AND br.eff_dt <= ?
                              ORDER BY br.eff_dt DESC, br.base_rate_id DESC
                              LIMIT 1
                          ), -999999) + margin_pct = ?
                      ))
                  )
                """,
                Integer.class,
                request.schmId(),
                request.rateTyp(),
                request.effDt(),
                request.rateTyp(),
                request.effRate(),
                request.rateTyp(),
                request.effDt(),
                request.effRate()
        );

        if (validRate == null || validRate == 0) {
            throw badRequest(
                    "Requested rate does not match an effective scheme rate"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        String username = authentication.getName();

        BankCreditLineFacility facility = new BankCreditLineFacility();

        facility.setClFacRef(
                "CL-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .toUpperCase()
        );

        facility.setCustId(request.custId());
        facility.setProdId(request.prodId());
        facility.setSchmId(request.schmId());

        facility.setClFacTyp(request.clFacTyp());
        facility.setCcyCd("MYR");

        facility.setApprLimit(request.apprLimit());
        facility.setUtilizedAmt(new BigDecimal("0.00"));

        facility.setEffDt(request.effDt());
        facility.setExpiryDt(request.expiryDt());

        facility.setRateTyp(request.rateTyp());
        facility.setEffRate(request.effRate());

        facility.setClFacSts("DRAFT");
        facility.setRemarks(request.remarks());

        facility.setCrtDt(now);
        facility.setCrtBy(username);
        facility.setUpdDt(now);
        facility.setUpdBy(username);

        return facilityRepository.saveAndFlush(facility);
    }

    @Transactional(readOnly = true)
    public BankCreditLineFacility get(Long facId) {
        return facilityRepository.findById(facId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Credit line facility not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<BankCreditLineFacility> getByCustomer(Long custId) {
        return facilityRepository.findByCustIdOrderByClFacIdDesc(custId);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}

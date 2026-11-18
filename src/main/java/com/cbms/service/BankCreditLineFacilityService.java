package com.cbms.service;

import com.cbms.dto.BankCreditLineFacilityCreateRequest;
import com.cbms.entity.BankCreditLineFacility;
import com.cbms.entity.BankProduct;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankCreditLineFacilityRepository;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;
import com.cbms.reference.BusinessReferenceGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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

        // Expiry is derived from scheme tenure in the UI; enforce the same scheme limits
        // for API clients (including calendar month-end dates).
        if (scheme.getMinTenor() != null &&
                request.expiryDt().isBefore(request.effDt().plusMonths(scheme.getMinTenor())))
            throw badRequest("Credit line tenure is below scheme minimum");
        if (scheme.getMaxTenor() != null &&
                request.expiryDt().isAfter(request.effDt().plusMonths(scheme.getMaxTenor())))
            throw badRequest("Credit line tenure is above scheme maximum");

        // Resolve only the latest effective-dated scheme version.  For floating
        // contracts capture the base code and margin NOW, independently of any
        // future scheme change.  effRate is the origination-rate snapshot.
        List<java.util.Map<String, Object>> pricing = jdbc.queryForList(
                """
                        SELECT rate_typ, fixed_rate_pct, min_rate_pct, max_rate_pct,
                               base_rate_cd, margin_pct
                        FROM public.bank_scheme_rate
                        WHERE schm_id = ? AND rate_sts = 'ACTIVE' AND eff_dt <= ?
                        ORDER BY eff_dt DESC, schm_rate_id DESC
                        LIMIT 1
                        """,
                request.schmId(), request.effDt());
        if (pricing.isEmpty()) {
            throw badRequest("No effective scheme rate is configured");
        }
        java.util.Map<String, Object> price = pricing.get(0);
        if (!request.rateTyp().equals(price.get("rate_typ"))) {
            throw badRequest("Rate type does not match scheme configuration");
        }

        String contractedBaseRate = null;
        BigDecimal contractedMargin = null;
        if ("FLOATING".equals(request.rateTyp())) {
            contractedBaseRate = (String) price.get("base_rate_cd");
            contractedMargin = (BigDecimal) price.get("margin_pct");
            if (contractedBaseRate == null || contractedMargin == null) {
                throw badRequest("Floating scheme requires a base rate and margin");
            }
            List<BigDecimal> currentBase = jdbc.queryForList(
                    """
                            SELECT rate_pct FROM public.bank_base_rate
                            WHERE rate_cd = ? AND ccy_cd = 'MYR'
                              AND rate_sts = 'ACTIVE' AND eff_dt <= ?
                            ORDER BY eff_dt DESC, base_rate_id DESC LIMIT 1
                            """,
                    BigDecimal.class, contractedBaseRate, request.effDt());
            if (currentBase.isEmpty()) {
                throw badRequest("No applicable MYR base rate exists");
            }
            BigDecimal calculatedRate;
            try {
                calculatedRate = BankCreditLineFloatingRateCalculator.effectiveRate(
                        currentBase.get(0), contractedMargin);
            } catch (IllegalArgumentException exception) {
                throw badRequest(exception.getMessage());
            }
            if (calculatedRate.compareTo(request.effRate()) != 0) {
                throw badRequest("Floating rate must equal base rate plus scheme margin");
            }
        } else {
            BigDecimal defaultRate = (BigDecimal) price.get("fixed_rate_pct");
            if (defaultRate == null) {
                throw badRequest("Scheme fixed rate is missing");
            }
            BigDecimal minimum = (BigDecimal) price.get("min_rate_pct");
            BigDecimal maximum = (BigDecimal) price.get("max_rate_pct");
            if (request.effRate().compareTo(minimum == null ? defaultRate : minimum) < 0
                    || request.effRate().compareTo(maximum == null ? defaultRate : maximum) > 0) {
                throw badRequest("Fixed rate is outside scheme bounds");
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        String username = authentication.getName();

        BankCreditLineFacility facility = new BankCreditLineFacility();

        facility.setClFacRef(BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.CREDIT_LINE, now));

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
        facility.setBaseRateCd(contractedBaseRate);
        facility.setMarginPct(contractedMargin);

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

package com.cbms.service;

import com.cbms.dto.BankFinancingApplicationCreateRequest;
import com.cbms.entity.BankFinancingApplication;
import com.cbms.entity.BankProduct;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankFinancingApplicationRepository;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BankFinancingApplicationService {

    private static final Set<String> FINANCING_CATEGORIES = Set.of(
            "HIRE_PURCHASE",
            "PERSONAL_FINANCING",
            "TERM_LOAN",
            "CREDIT_LINE",
            "FLOOR_STOCKING"
    );

    private final BankFinancingApplicationRepository applicationRepository;
    private final BankProductRepository productRepository;
    private final BankSchemeRepository schemeRepository;
    private final JdbcTemplate jdbcTemplate;

    public BankFinancingApplicationService(
            BankFinancingApplicationRepository applicationRepository,
            BankProductRepository productRepository,
            BankSchemeRepository schemeRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.schemeRepository = schemeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BankFinancingApplication create(
            BankFinancingApplicationCreateRequest request,
            Authentication authentication
    ) {
        Integer customerCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                Integer.class,
                request.custId()
        );

        if (customerCount == null || customerCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer not found"
            );
        }

        BankProduct product = productRepository
                .findById(request.prodId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product not found"
                ));

        BankScheme scheme = schemeRepository
                .findById(request.schmId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Scheme not found"
                ));

        if (!"ACTIVE".equals(product.getProdSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is not ACTIVE"
            );
        }

        if (!"ACTIVE".equals(scheme.getSchmSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme is not ACTIVE"
            );
        }

        if (!request.prodId().equals(scheme.getProdId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme does not belong to product"
            );
        }

        String category = product.getProdCat() == null
                ? ""
                : product.getProdCat().trim().toUpperCase(Locale.ROOT);

        if (!FINANCING_CATEGORIES.contains(category)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Selected product is not a financing product"
            );
        }

        String bankingType = product.getBankingTyp() == null
                ? ""
                : product.getBankingTyp().trim().toUpperCase(Locale.ROOT);

        if (!Set.of("ISLAMIC", "CONVENTIONAL").contains(bankingType)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unsupported banking type"
            );
        }

        if (!"MYR".equalsIgnoreCase(product.getCcyCd())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only MYR financing is currently supported"
            );
        }

        if (scheme.getMinAmt() != null
                && request.reqAmt().compareTo(scheme.getMinAmt()) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requested amount is below scheme minimum"
            );
        }

        if (scheme.getMaxAmt() != null
                && request.reqAmt().compareTo(scheme.getMaxAmt()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requested amount exceeds scheme maximum"
            );
        }

        if (scheme.getMinTenor() != null
                && request.reqTenor() < scheme.getMinTenor()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requested tenure is below scheme minimum"
            );
        }

        if (scheme.getMaxTenor() != null
                && request.reqTenor() > scheme.getMaxTenor()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requested tenure exceeds scheme maximum"
            );
        }

        BankFinancingApplication application =
                new BankFinancingApplication();

        application.setApplRef(
                "FIN-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .toUpperCase(Locale.ROOT)
        );

        application.setCustId(request.custId());
        application.setProdId(request.prodId());
        application.setSchmId(request.schmId());

        application.setFacTyp(category);
        application.setBankingTyp(bankingType);
        application.setCcyCd("MYR");

        application.setReqAmt(request.reqAmt());
        application.setReqTenor(request.reqTenor());
        application.setFinancingPurpose(request.financingPurpose());
        application.setRemarks(request.remarks());

        application.setApplSts("DRAFT");

        application.setCrtBy(authentication.getName());
        application.setUpdBy(authentication.getName());

        return applicationRepository.saveAndFlush(application);
    }

    @Transactional(readOnly = true)
    public BankFinancingApplication get(Long applId) {
        return applicationRepository.findById(applId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Financing application not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<BankFinancingApplication> list(
            Long custId,
            String status
    ) {
        if (custId != null) {
            return applicationRepository.findByCustIdOrderByApplIdDesc(custId);
        }

        if (status != null && !status.isBlank()) {
            return applicationRepository.findByApplStsOrderByApplIdDesc(
                    status.trim().toUpperCase(Locale.ROOT)
            );
        }

        return applicationRepository.findAllByOrderByApplIdDesc();
    }
}

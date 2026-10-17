package com.cbms.service;

import com.cbms.dto.BankAccountApplicationCreateRequest;
import com.cbms.dto.BankAccountApplicationResponse;
import com.cbms.entity.BankAccountApplication;
import com.cbms.entity.BankProduct;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankAccountApplicationRepository;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BankAccountApplicationService {

    private final BankAccountApplicationRepository applicationRepository;
    private final BankProductRepository productRepository;
    private final BankSchemeRepository schemeRepository;
    private final JdbcTemplate jdbcTemplate;

    public BankAccountApplicationService(
            BankAccountApplicationRepository applicationRepository,
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
    public BankAccountApplicationResponse create(
            BankAccountApplicationCreateRequest request,
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
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        BankProduct product = productRepository.findById(request.prodId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        BankScheme scheme = schemeRepository.findById(request.schmId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Scheme not found"
                ));

        if (!scheme.getProdId().equals(product.getProdId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Scheme does not belong to selected product"
            );
        }

        if (!"ACTIVE".equals(product.getProdSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product must be ACTIVE"
            );
        }

        if (!"ACTIVE".equals(scheme.getSchmSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme must be ACTIVE"
            );
        }

        validateSchemeLimits(request, scheme);

        BankAccountApplication application = new BankAccountApplication();

        application.setCustId(request.custId());
        application.setProdId(request.prodId());
        application.setSchmId(request.schmId());

        application.setApplRef(
                "APP-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 20)
                        .toUpperCase()
        );

        application.setApplSts("DRAFT");
        application.setReqAmt(request.reqAmt());
        application.setReqTenor(request.reqTenor());
        application.setRemarks(request.remarks());

        String username = authentication.getName();
        application.setCrtBy(username);
        application.setUpdBy(username);

        return toResponse(applicationRepository.saveAndFlush(application));
    }

    @Transactional(readOnly = true)
    public BankAccountApplicationResponse get(Long applId) {
        BankAccountApplication application =
                applicationRepository.findById(applId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Application not found"
                        ));

        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public List<BankAccountApplicationResponse> list(
            Long custId,
            String status
    ) {
        List<BankAccountApplication> applications;

        if (custId != null) {
            applications = applicationRepository
                    .findByCustIdOrderByCrtDtDesc(custId);
        } else if (status != null) {
            applications = applicationRepository
                    .findByApplStsOrderByCrtDtDesc(status);
        } else {
            applications = applicationRepository
                    .findAllByOrderByCrtDtDesc();
        }

        return applications.stream()
                .filter(a -> status == null
                        || status.equals(a.getApplSts()))
                .map(this::toResponse)
                .toList();
    }

    private void validateSchemeLimits(
            BankAccountApplicationCreateRequest request,
            BankScheme scheme
    ) {
        BigDecimal amount = request.reqAmt();

        if (amount != null) {
            if (scheme.getMinAmt() != null
                    && amount.compareTo(scheme.getMinAmt()) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Requested amount is below scheme minimum"
                );
            }

            if (scheme.getMaxAmt() != null
                    && amount.compareTo(scheme.getMaxAmt()) > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Requested amount exceeds scheme maximum"
                );
            }
        }

        Integer tenor = request.reqTenor();

        if (tenor != null) {
            if (scheme.getMinTenor() != null
                    && tenor < scheme.getMinTenor()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Requested tenure is below scheme minimum"
                );
            }

            if (scheme.getMaxTenor() != null
                    && tenor > scheme.getMaxTenor()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Requested tenure exceeds scheme maximum"
                );
            }
        }
    }

    private BankAccountApplicationResponse toResponse(
            BankAccountApplication application
    ) {
        return new BankAccountApplicationResponse(
                application.getApplId(),
                application.getApplRef(),
                application.getCustId(),
                application.getProdId(),
                application.getSchmId(),
                application.getApplSts(),
                application.getReqAmt(),
                application.getReqTenor(),
                application.getRemarks(),
                application.getAcctId(),
                application.getCrtDt(),
                application.getCrtBy(),
                application.getUpdDt(),
                application.getUpdBy()
        );
    }
}

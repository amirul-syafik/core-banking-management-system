package com.cbms.service;

import com.cbms.dto.BankSchemeCreateRequest;
import com.cbms.dto.BankSchemeResponse;
import com.cbms.entity.BankProduct;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.cbms.dto.BankSchemeUpdateRequest;
import com.cbms.dto.BankSchemeStatusRequest;
import com.cbms.entity.BankSchemeAudit;
import com.cbms.repository.BankSchemeAuditRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class BankSchemeService {

    private final BankSchemeRepository schemeRepository;
    private final BankProductRepository productRepository;
    private final BankSchemeAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public BankSchemeService(
            BankSchemeRepository schemeRepository,
            BankProductRepository productRepository,
            BankSchemeAuditRepository auditRepository,
            ObjectMapper objectMapper
    ) {
        this.schemeRepository = schemeRepository;
        this.productRepository = productRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BankSchemeResponse create(
            BankSchemeCreateRequest request,
            Authentication authentication
    ) {
        BankProduct product = productRepository.findById(request.prodId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Parent product not found"
                ));

        if ("INACTIVE".equals(product.getProdSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot create a scheme for an inactive product"
            );
        }

        if (schemeRepository.existsByProdIdAndSchmCd(
                request.prodId(), request.schmCd())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme code already exists for this product"
            );
        }

        if (request.minAmt() != null && request.maxAmt() != null
                && request.minAmt().compareTo(request.maxAmt()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum amount cannot exceed maximum amount"
            );
        }

        if (request.minTenor() != null && request.maxTenor() != null
                && request.minTenor() > request.maxTenor()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum tenure cannot exceed maximum tenure"
            );
        }

        BankScheme scheme = new BankScheme();
        scheme.setProdId(product.getProdId());
        scheme.setSchmCd(request.schmCd());
        scheme.setSchmNm(request.schmNm().trim());
        scheme.setSchmDesc(request.schmDesc());
        scheme.setMinAmt(request.minAmt());
        scheme.setMaxAmt(request.maxAmt());
        scheme.setMinTenor(request.minTenor());
        scheme.setMaxTenor(request.maxTenor());
        scheme.setEligibilityDesc(request.eligibilityDesc());
        scheme.setSchmSts("DRAFT");

        String username = authentication.getName();
        scheme.setCrtBy(username);
        scheme.setUpdBy(username);

        try {
            BankScheme saved = schemeRepository.saveAndFlush(scheme);

            saveAudit(
                    saved.getSchmId(),
                    "CREATE",
                    null,
                    snapshot(saved),
                    authentication.getName()
            );

            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme could not be created due to a data conflict",
                    ex
            );
        }
    }

    @Transactional(readOnly = true)
    public BankSchemeResponse get(Long schmId) {
        BankScheme scheme = schemeRepository.findById(schmId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Scheme not found"
                ));

        return toResponse(scheme);
    }

    @Transactional(readOnly = true)
    public List<BankSchemeResponse> list(Long prodId) {
        List<BankScheme> schemes;

        if (prodId == null) {
            schemes = schemeRepository
                    .findAllByOrderByProdIdAscSchmCdAsc();
        } else {
            schemes = schemeRepository
                    .findByProdIdOrderBySchmCdAsc(prodId);
        }

        return schemes.stream()
                .map(this::toResponse)
                .toList();
    }

    private BankSchemeResponse toResponse(BankScheme scheme) {
        return new BankSchemeResponse(
                scheme.getSchmId(),
                scheme.getProdId(),
                scheme.getSchmCd(),
                scheme.getSchmNm(),
                scheme.getSchmDesc(),
                scheme.getMinAmt(),
                scheme.getMaxAmt(),
                scheme.getMinTenor(),
                scheme.getMaxTenor(),
                scheme.getEligibilityDesc(),
                scheme.getSchmSts(),
                scheme.getCrtDt(),
                scheme.getCrtBy(),
                scheme.getUpdDt(),
                scheme.getUpdBy()
        );
    }

    @Transactional
    public BankSchemeResponse update(
            Long schmId,
            BankSchemeUpdateRequest request,
            Authentication authentication
    ) {
        BankScheme scheme = schemeRepository.findById(schmId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Scheme not found"
                ));

        if (!"DRAFT".equals(scheme.getSchmSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only DRAFT schemes can be modified"
            );
        }

        validateRanges(
                request.minAmt(), request.maxAmt(),
                request.minTenor(), request.maxTenor()
        );

        String before = snapshot(scheme);

        scheme.setSchmNm(request.schmNm().trim());
        scheme.setSchmDesc(request.schmDesc());
        scheme.setMinAmt(request.minAmt());
        scheme.setMaxAmt(request.maxAmt());
        scheme.setMinTenor(request.minTenor());
        scheme.setMaxTenor(request.maxTenor());
        scheme.setEligibilityDesc(request.eligibilityDesc());
        scheme.setUpdBy(authentication.getName());

        schemeRepository.saveAndFlush(scheme);

        saveAudit(
                scheme.getSchmId(),
                "UPDATE",
                before,
                snapshot(scheme),
                authentication.getName()
        );

        return toResponse(scheme);
    }

    @Transactional
    public BankSchemeResponse changeStatus(
            Long schmId,
            BankSchemeStatusRequest request,
            Authentication authentication
    ) {
        BankScheme scheme = schemeRepository.findById(schmId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Scheme not found"
                ));

        String current = scheme.getSchmSts();
        String target = request.status();

        if (current.equals(target)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme already has the requested status"
            );
        }

        boolean validTransition =
                ("DRAFT".equals(current) && "ACTIVE".equals(target))
                        || ("ACTIVE".equals(current) && "INACTIVE".equals(target))
                        || ("INACTIVE".equals(current) && "ACTIVE".equals(target));

        if (!validTransition) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid scheme status transition"
            );
        }

        if ("ACTIVE".equals(target)) {
            BankProduct product = productRepository
                    .findById(scheme.getProdId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Parent product not found"
                    ));

            if (!"ACTIVE".equals(product.getProdSts())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Parent product must be ACTIVE"
                );
            }

            validateRanges(
                    scheme.getMinAmt(), scheme.getMaxAmt(),
                    scheme.getMinTenor(), scheme.getMaxTenor()
            );
        }

        String before = snapshot(scheme);

        scheme.setSchmSts(target);
        scheme.setUpdBy(authentication.getName());

        schemeRepository.saveAndFlush(scheme);

        saveAudit(
                scheme.getSchmId(),
                "STATUS_CHANGE",
                before,
                snapshot(scheme),
                authentication.getName()
        );

        return toResponse(scheme);
    }

    @Transactional(readOnly = true)
    public java.util.List<BankSchemeAudit> getAuditHistory(Long schmId) {
        if (!schemeRepository.existsById(schmId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Scheme not found"
            );
        }

        return auditRepository
                .findBySchmIdOrderByActionDtDescAuditIdDesc(schmId);
    }

    private void validateRanges(
            java.math.BigDecimal minAmt,
            java.math.BigDecimal maxAmt,
            Integer minTenor,
            Integer maxTenor
    ) {
        if (minAmt != null && maxAmt != null
                && minAmt.compareTo(maxAmt) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum amount cannot exceed maximum amount"
            );
        }

        if (minTenor != null && maxTenor != null
                && minTenor > maxTenor) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum tenure cannot exceed maximum tenure"
            );
        }
    }

    private String snapshot(BankScheme scheme) {
        return objectMapper.writeValueAsString(toResponse(scheme));
    }

    private void saveAudit(
            Long schmId,
            String action,
            String before,
            String after,
            String username
    ) {
        BankSchemeAudit audit = new BankSchemeAudit();
        audit.setSchmId(schmId);
        audit.setActionTyp(action);
        audit.setOldValue(before);
        audit.setNewValue(after);
        audit.setActionBy(username);

        auditRepository.save(audit);
    }
}

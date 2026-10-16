package com.cbms.service;

import com.cbms.dto.BankProductCreateRequest;
import com.cbms.dto.BankProductResponse;
import com.cbms.entity.BankProduct;
import com.cbms.repository.BankProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.cbms.dto.BankProductUpdateRequest;
import com.cbms.dto.BankProductStatusRequest;
import com.cbms.entity.BankProductAudit;
import com.cbms.repository.BankProductAuditRepository;
import org.springframework.http.HttpStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class BankProductService {

    private final BankProductRepository repository;
    private final BankProductAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public BankProductService(
            BankProductRepository repository,
            BankProductAuditRepository auditRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BankProductResponse create(
            BankProductCreateRequest request,
            Authentication authentication
    ) {
        String code = request.prodCd();

        if (repository.existsByProdCd(code)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product code already exists"
            );
        }

        BankProduct product = new BankProduct();
        product.setProdCd(code);
        product.setProdNm(request.prodNm().trim());
        product.setProdCat(request.prodCat());
        product.setBankingTyp(request.bankingTyp());
        product.setCcyCd(request.ccyCd());
        product.setProdDesc(request.prodDesc());
        product.setProdSts("DRAFT");

        String username = authentication.getName();
        product.setCrtBy(username);
        product.setUpdBy(username);

        try {
            BankProduct saved = repository.saveAndFlush(product);

            saveAudit(
                    saved.getProdId(),
                    "CREATE",
                    null,
                    snapshot(saved),
                    authentication.getName()
            );

            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product could not be created due to a data conflict",
                    ex
            );
        }
    }

    @Transactional(readOnly = true)
    public BankProductResponse get(Long prodId) {
        BankProduct product = repository.findById(prodId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<BankProductResponse> list(
            String category,
            String status
    ) {
        if (category != null && status != null) {
            return repository.findAllByOrderByProdCdAsc()
                    .stream()
                    .filter(p -> category.equals(p.getProdCat()))
                    .filter(p -> status.equals(p.getProdSts()))
                    .map(this::toResponse)
                    .toList();
        }

        List<BankProduct> products;

        if (category != null) {
            products = repository.findByProdCatOrderByProdCdAsc(category);
        } else if (status != null) {
            products = repository.findByProdStsOrderByProdCdAsc(status);
        } else {
            products = repository.findAllByOrderByProdCdAsc();
        }

        return products.stream()
                .map(this::toResponse)
                .toList();
    }

    private BankProductResponse toResponse(BankProduct product) {
        return new BankProductResponse(
                product.getProdId(),
                product.getProdCd(),
                product.getProdNm(),
                product.getProdCat(),
                product.getBankingTyp(),
                product.getCcyCd(),
                product.getProdDesc(),
                product.getProdSts(),
                product.getCrtDt(),
                product.getCrtBy(),
                product.getUpdDt(),
                product.getUpdBy()
        );
    }

    @Transactional
    public BankProductResponse update(
            Long prodId,
            BankProductUpdateRequest request,
            Authentication authentication
    ) {
        BankProduct product = repository.findById(prodId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found"));

        if (!"DRAFT".equals(product.getProdSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only DRAFT products can be modified"
            );
        }

        String before = snapshot(product);

        product.setProdNm(request.prodNm().trim());
        product.setProdDesc(request.prodDesc());
        product.setUpdBy(authentication.getName());

        repository.saveAndFlush(product);

        saveAudit(
                product.getProdId(),
                "UPDATE",
                before,
                snapshot(product),
                authentication.getName()
        );

        return toResponse(product);
    }

    @Transactional
    public BankProductResponse changeStatus(
            Long prodId,
            BankProductStatusRequest request,
            Authentication authentication
    ) {
        BankProduct product = repository.findById(prodId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found"));

        String currentStatus = product.getProdSts();
        String requestedStatus = request.status();

        if (currentStatus.equals(requestedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is already in the requested status"
            );
        }

        boolean validTransition =
                ("DRAFT".equals(currentStatus)
                        && "ACTIVE".equals(requestedStatus))
                        || ("ACTIVE".equals(currentStatus)
                        && "INACTIVE".equals(requestedStatus))
                        || ("INACTIVE".equals(currentStatus)
                        && "ACTIVE".equals(requestedStatus));

        if (!validTransition) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid product status transition"
            );
        }

        String before = snapshot(product);

        product.setProdSts(requestedStatus);
        product.setUpdBy(authentication.getName());

        repository.saveAndFlush(product);

        saveAudit(
                product.getProdId(),
                "STATUS_CHANGE",
                before,
                snapshot(product),
                authentication.getName()
        );

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public java.util.List<BankProductAudit> getAuditHistory(Long prodId) {
        if (!repository.existsById(prodId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Product not found"
            );
        }

        return auditRepository
                .findByProdIdOrderByActionDtDescAuditIdDesc(prodId);
    }

    private String snapshot(BankProduct product) {
        try {
            return objectMapper.writeValueAsString(toResponse(product));
        } catch (JacksonException ex) {
            throw new IllegalStateException(
                    "Unable to serialize product audit snapshot", ex);
        }
    }

    private void saveAudit(
            Long prodId,
            String action,
            String before,
            String after,
            String username
    ) {
        BankProductAudit audit = new BankProductAudit();
        audit.setProdId(prodId);
        audit.setActionTyp(action);
        audit.setOldValue(before);
        audit.setNewValue(after);
        audit.setActionBy(username);

        auditRepository.save(audit);
    }
}

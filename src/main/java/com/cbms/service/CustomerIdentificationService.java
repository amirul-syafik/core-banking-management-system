package com.cbms.service;

import com.cbms.dto.CustomerIdentificationRequest;
import com.cbms.dto.CustomerIdentificationResponse;
import com.cbms.entity.CustomerIdentification;
import com.cbms.repository.CustomerIdentificationRepository;
import com.cbms.repository.CustomerDocumentRepository;
import com.cbms.repository.CustomerRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerIdentificationService {

    private final CustomerIdentificationRepository identificationRepository;
    private final CustomerDocumentRepository documentRepository;
    private final CustomerRepository customerRepository;

    public CustomerIdentificationService(
            CustomerIdentificationRepository identificationRepository,
            CustomerDocumentRepository documentRepository,
            CustomerRepository customerRepository
    ) {
        this.identificationRepository = identificationRepository;
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerIdentificationResponse create(
            Long custId,
            CustomerIdentificationRequest request,
            Authentication authentication
    ) {
        if (!customerRepository.existsById(custId)) {
            throw new IllegalArgumentException(
                    "Customer not found: " + custId
            );
        }

        if (request.issDt() != null
                && request.expDt() != null
                && request.expDt().isBefore(request.issDt())) {
            throw new IllegalArgumentException(
                    "Expiry date cannot be earlier than issue date"
            );
        }

        if (request.docId() != null
                && documentRepository.findByDocIdAndCustId(
                request.docId(), custId
        ).isEmpty()) {
            throw new IllegalArgumentException(
                    "Uploaded document does not belong to this customer"
            );
        }

        CustomerIdentification identification =
                new CustomerIdentification();

        identification.setCustId(custId);
        identification.setDocId(request.docId());
        identification.setDocTyp(
                request.docTyp().trim().toUpperCase(Locale.ROOT)
        );
        identification.setDocNo(request.docNo().trim());
        identification.setIssCty(
                request.issCty().toUpperCase(Locale.ROOT)
        );
        identification.setIssDt(request.issDt());
        identification.setExpDt(request.expDt());
        identification.setPriFlg(
                Boolean.TRUE.equals(request.priFlg())
        );

        String username = authentication.getName();
        OffsetDateTime now = OffsetDateTime.now();

        identification.setCrtBy(username);
        identification.setUpdBy(username);
        identification.setCrtDt(now);
        identification.setUpdDt(now);

        try {
            CustomerIdentification saved =
                    identificationRepository.saveAndFlush(
                            identification
                    );

            return toResponse(saved);

        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "Identification violates a database constraint",
                    ex
            );
        }
    }

    @Transactional(readOnly = true)
    public List<CustomerIdentificationResponse> list(Long custId) {

        if (!customerRepository.existsById(custId)) {
            throw new IllegalArgumentException(
                    "Customer not found: " + custId
            );
        }

        return identificationRepository
                .findByCustIdOrderByIdnIdDesc(custId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerIdentificationResponse get(
            Long custId,
            Long idnId
    ) {
        CustomerIdentification identification =
                identificationRepository
                        .findByIdnIdAndCustId(idnId, custId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Identification not found"
                                )
                        );

        return toResponse(identification);
    }

    private CustomerIdentificationResponse toResponse(
            CustomerIdentification identification
    ) {
        return new CustomerIdentificationResponse(
                identification.getIdnId(),
                identification.getCustId(),
                identification.getDocTyp(),
                maskDocumentNumber(identification.getDocNo()),
                identification.getIssCty(),
                identification.getIssDt(),
                identification.getExpDt(),
                identification.getPriFlg(),
                identification.getDocId(),
                identification.getCrtBy(),
                identification.getCrtDt()
        );
    }

    private String maskDocumentNumber(String documentNumber) {

        if (documentNumber == null) {
            return null;
        }

        if (documentNumber.length() <= 4) {
            return "*".repeat(documentNumber.length());
        }

        return "*".repeat(documentNumber.length() - 4)
                + documentNumber.substring(
                documentNumber.length() - 4
        );
    }

    @Transactional
    public CustomerIdentificationResponse update(Long custId, Long idnId, CustomerIdentificationRequest request,
                                                 Authentication authentication) {
        CustomerIdentification record = identificationRepository.findByIdnIdAndCustId(idnId, custId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Identification not found"));
        if (request.issDt() != null && request.expDt() != null && request.expDt().isBefore(request.issDt()))
            throw new IllegalArgumentException("Expiry date cannot be before issue date");
        if (request.docId() != null && documentRepository.findByDocIdAndCustId(request.docId(), custId).isEmpty())
            throw new IllegalArgumentException("Document does not belong to customer");
        record.setDocTyp(request.docTyp().trim().toUpperCase(Locale.ROOT));
        record.setDocNo(request.docNo().trim());
        record.setIssCty(request.issCty().trim().toUpperCase(Locale.ROOT));
        record.setIssDt(request.issDt());
        record.setExpDt(request.expDt());
        record.setPriFlg(Boolean.TRUE.equals(request.priFlg()));
        record.setDocId(request.docId());
        record.setUpdDt(OffsetDateTime.now());
        record.setUpdBy(authentication.getName());
        try {
            return toResponse(identificationRepository.saveAndFlush(record));
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Identification violates a database constraint", ex);
        }
    }
}

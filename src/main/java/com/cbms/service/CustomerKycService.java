package com.cbms.service;

import com.cbms.dto.CustomerKycRequest;
import com.cbms.entity.CustomerKyc;
import com.cbms.repository.CustomerKycRepository;
import com.cbms.repository.CustomerRepository;

import com.cbms.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerKycService {

    private final CustomerKycRepository kycRepository;
    private final CustomerRepository customerRepository;

    public CustomerKycService(
            CustomerKycRepository kycRepository,
            CustomerRepository customerRepository) {
        this.kycRepository = kycRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerKyc createAssessment(
            Long custId,
            CustomerKycRequest request) {

        if (!customerRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        validateAssessment(request);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CustomerKyc kyc = new CustomerKyc();
        kyc.setCustId(custId);
        apply(kyc, request);
        kyc.setRevDt(request.getRevDt());
        kyc.setCrtDt(now);
        kyc.setCrtBy(CurrentActor.username());
        kyc.setUpdDt(now);
        kyc.setUpdBy(null);

        return kycRepository.save(kyc);
    }

    @Transactional(readOnly = true)
    public List<CustomerKyc> getAssessments(Long custId) {

        if (!customerRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        return kycRepository.findByCustIdOrderByUpdDtDescKycIdDesc(custId);
    }

    @Transactional
    public CustomerKyc update(Long custId, Long recordId, CustomerKycRequest request, org.springframework.security.core.Authentication authentication) {
        CustomerKyc record = kycRepository.findById(recordId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
        if (!customerRepository.existsById(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        if (!record.getCustId().equals(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found for customer");
        validateAssessment(request);
        apply(record, request);
        record.setRevDt(request.getRevDt());
        record.setUpdDt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setUpdBy(CurrentActor.username());
        return kycRepository.save(record);
    }

    private void validateAssessment(CustomerKycRequest request) {
        if (request.getRevDt() != null && request.getNxtRevDt() != null
                && request.getNxtRevDt().isBefore(request.getRevDt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Next review date cannot be before the review date");
        }
        if ("VERIFIED".equalsIgnoreCase(request.getAsmtSts())
                && (request.getRevDt() == null || request.getNxtRevDt() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A verified KYC assessment requires review and next review dates");
        }
    }

    private void apply(CustomerKyc record, CustomerKycRequest request) {
        record.setAsmtSts(request.getAsmtSts().trim().toUpperCase(Locale.ROOT));
        record.setRiskRtng(request.getRiskRtng().trim().toUpperCase(Locale.ROOT));
        record.setNxtRevDt(request.getNxtRevDt());
        record.setRmk(request.getRmk() == null ? null : request.getRmk().trim());
    }
}

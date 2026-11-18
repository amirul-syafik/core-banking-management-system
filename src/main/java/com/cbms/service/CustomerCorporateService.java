package com.cbms.service;

import com.cbms.dto.CustomerCorporateRequest;
import com.cbms.entity.Customer;
import com.cbms.entity.CustomerCorporate;
import com.cbms.repository.CustomerCorporateRepository;
import com.cbms.repository.CustomerRepository;
import com.cbms.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class CustomerCorporateService {

    private final CustomerRepository customerRepository;
    private final CustomerCorporateRepository corporateRepository;

    public CustomerCorporateService(
            CustomerRepository customerRepository,
            CustomerCorporateRepository corporateRepository) {
        this.customerRepository = customerRepository;
        this.corporateRepository = corporateRepository;
    }

    @Transactional
    public CustomerCorporate createCorporateDetails(
            Long custId,
            CustomerCorporateRequest request) {

        Customer customer = customerRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));

        if (!"CORPORATE".equals(customer.getCustTyp())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Corporate details can only be added to corporate customers"
            );
        }

        String regCty = normalizeCode(request.getRegCty());
        String regNo = trimToNull(request.getRegNo());

        if (regNo != null && regCty != null
                && corporateRepository.existsByRegCtyAndRegNo(regCty, regNo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Registration number already exists for this country"
            );
        }

        if (corporateRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Corporate details already exist for this customer"
            );
        }

        if (request.getIncDt() != null
                && request.getIncDt().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Incorporation date cannot be in the future"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CustomerCorporate corporate = new CustomerCorporate();
        corporate.setCustId(custId);
        corporate.setRegNo(regNo);
        corporate.setRegCty(regCty);
        corporate.setIncDt(request.getIncDt());
        corporate.setBizTyp(trimToNull(request.getBizTyp()));
        corporate.setCrtDt(now);
        corporate.setCrtBy(CurrentActor.username());
        corporate.setUpdDt(now);
        corporate.setUpdBy(null);

        return corporateRepository.save(corporate);
    }

    @Transactional(readOnly = true)
    public CustomerCorporate getCorporateDetails(Long custId) {

        return corporateRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Corporate details not found"
                ));
    }

    @Transactional
    public CustomerCorporate update(Long custId, CustomerCorporateRequest request, org.springframework.security.core.Authentication authentication) {
        CustomerCorporate record = corporateRepository.findById(custId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
        if (!customerRepository.existsById(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        var customer = customerRepository.findById(custId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (!"CORPORATE".equalsIgnoreCase(customer.getCustTyp()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong customer type");
        if (request.getIncDt() != null && request.getIncDt().isAfter(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorporation date in future");
        String regNo = trimToNull(request.getRegNo());
        String regCty = normalizeCode(request.getRegCty());
        if (regNo != null && regCty != null && corporateRepository.existsByRegCtyAndRegNo(regCty, regNo)
                && !(java.util.Objects.equals(record.getRegNo(), regNo) && java.util.Objects.equals(record.getRegCty(), regCty)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registration number already in use");
        record.setRegNo(regNo);
        record.setRegCty(regCty);
        record.setIncDt(request.getIncDt());
        record.setBizTyp(trimToNull(request.getBizTyp()));
        record.setUpdDt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setUpdBy(CurrentActor.username());
        return corporateRepository.save(record);
    }

    private String normalizeCode(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

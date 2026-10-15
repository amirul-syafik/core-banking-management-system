package com.cbms.service;

import com.cbms.dto.CustomerCorporateRequest;
import com.cbms.entity.Customer;
import com.cbms.entity.CustomerCorporate;
import com.cbms.repository.CustomerCorporateRepository;
import com.cbms.repository.CustomerRepository;
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

        String regCty = request.getRegCty() == null
                ? null : request.getRegCty().toUpperCase();

        String regNo = request.getRegNo() == null
                ? null : request.getRegNo().trim();

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
        corporate.setBizTyp(request.getBizTyp() == null
                ? null : request.getBizTyp().trim());
        corporate.setCrtDt(now);
        corporate.setCrtBy("SYSTEM");
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
}

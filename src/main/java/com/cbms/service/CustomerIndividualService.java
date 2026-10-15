package com.cbms.service;

import com.cbms.dto.CustomerIndividualRequest;
import com.cbms.entity.Customer;
import com.cbms.entity.CustomerIndividual;
import com.cbms.repository.CustomerIndividualRepository;
import com.cbms.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class CustomerIndividualService {

    private final CustomerRepository customerRepository;
    private final CustomerIndividualRepository individualRepository;

    public CustomerIndividualService(
            CustomerRepository customerRepository,
            CustomerIndividualRepository individualRepository) {
        this.customerRepository = customerRepository;
        this.individualRepository = individualRepository;
    }

    @Transactional
    public CustomerIndividual createIndividualDetails(
            Long custId,
            CustomerIndividualRequest request) {

        Customer customer = customerRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));

        if (!"INDIVIDUAL".equals(customer.getCustTyp())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Individual details can only be added to individual customers"
            );
        }

        if (individualRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Individual details already exist for this customer"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CustomerIndividual individual = new CustomerIndividual();
        individual.setCustId(custId);
        individual.setDob(request.getDob());
        individual.setGndr(request.getGndr());
        individual.setNatCd(request.getNatCd() == null
                ? null : request.getNatCd().toUpperCase());
        individual.setMrtlSts(request.getMrtlSts());
        individual.setCrtDt(now);
        individual.setCrtBy("SYSTEM");
        individual.setUpdDt(now);
        individual.setUpdBy(null);

        return individualRepository.save(individual);
    }

    @Transactional(readOnly = true)
    public CustomerIndividual getIndividualDetails(Long custId) {

        return individualRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Individual details not found"
                ));
    }
}

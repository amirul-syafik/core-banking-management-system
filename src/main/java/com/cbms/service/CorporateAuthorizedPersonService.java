package com.cbms.service;

import com.cbms.dto.CorporateAuthorizedPersonRequest;
import com.cbms.entity.CorporateAuthorizedPerson;
import com.cbms.entity.Customer;
import com.cbms.repository.CorporateAuthorizedPersonRepository;
import com.cbms.repository.CustomerRepository;
import com.cbms.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class CorporateAuthorizedPersonService {

    private final CorporateAuthorizedPersonRepository authorizedPersonRepository;
    private final CustomerRepository customerRepository;

    public CorporateAuthorizedPersonService(
            CorporateAuthorizedPersonRepository authorizedPersonRepository,
            CustomerRepository customerRepository) {
        this.authorizedPersonRepository = authorizedPersonRepository;
        this.customerRepository = customerRepository;
    }

    public CorporateAuthorizedPerson create(
            Long corpCustId,
            CorporateAuthorizedPersonRequest request) {

        Customer corporate = customerRepository.findById(corpCustId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Corporate customer not found"));

        if (!"CORPORATE".equalsIgnoreCase(corporate.getCustTyp())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The customer must be a CORPORATE customer");
        }

        Customer authorizedCustomer = customerRepository
                .findById(request.getAuthCustId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Authorized customer not found"));

        if (corpCustId.equals(request.getAuthCustId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A corporate customer cannot authorize itself");
        }

        if (!"INDIVIDUAL".equalsIgnoreCase(authorizedCustomer.getCustTyp())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The authorized person must be an INDIVIDUAL customer");
        }

        LocalDate effectiveDate = request.getEffDt() != null
                ? request.getEffDt()
                : LocalDate.now(ZoneOffset.UTC);

        LocalDate endDate = request.getEndDt();

        if (endDate != null && endDate.isBefore(effectiveDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "End date cannot be before the effective date");
        }

        String authTyp = request.getAuthTyp().trim().toUpperCase();
        String status = request.getSts() == null || request.getSts().isBlank()
                ? "ACTIVE"
                : request.getSts().trim().toUpperCase();

        if (authorizedPersonRepository
                .existsByCorpCustIdAndAuthCustIdAndAuthTypAndEffDt(
                        corpCustId,
                        request.getAuthCustId(),
                        authTyp,
                        effectiveDate)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This authorization record already exists");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CorporateAuthorizedPerson person = new CorporateAuthorizedPerson();
        person.setCorpCustId(corpCustId);
        person.setAuthCustId(request.getAuthCustId());
        person.setAuthTyp(authTyp);
        person.setAuthRef(request.getAuthRef() == null
                ? null : request.getAuthRef().trim());
        person.setEffDt(effectiveDate);
        person.setEndDt(endDate);
        person.setSts(status);
        person.setRmk(request.getRmk() == null
                ? null : request.getRmk().trim());
        person.setCrtDt(now);
        person.setCrtBy(CurrentActor.username());
        person.setUpdDt(now);
        person.setUpdBy(null);

        return authorizedPersonRepository.save(person);
    }

    public List<CorporateAuthorizedPerson> findByCorporateCustomer(Long corpCustId) {
        if (!customerRepository.existsById(corpCustId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Corporate customer not found");
        }

        return authorizedPersonRepository.findByCorpCustId(corpCustId);
    }
}

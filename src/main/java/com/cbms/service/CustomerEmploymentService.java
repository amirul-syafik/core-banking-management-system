package com.cbms.service;

import com.cbms.dto.CustomerEmploymentRequest;
import com.cbms.entity.Customer;
import com.cbms.entity.CustomerEmployment;
import com.cbms.repository.CustomerEmploymentRepository;
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
public class CustomerEmploymentService {

    private final CustomerEmploymentRepository employmentRepository;
    private final CustomerRepository customerRepository;

    public CustomerEmploymentService(
            CustomerEmploymentRepository employmentRepository,
            CustomerRepository customerRepository) {
        this.employmentRepository = employmentRepository;
        this.customerRepository = customerRepository;
    }

    public CustomerEmployment create(
            Long custId,
            CustomerEmploymentRequest request) {

        Customer customer = customerRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));

        if (!"INDIVIDUAL".equalsIgnoreCase(customer.getCustTyp())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employment details can only be added to an individual customer");
        }

        if (request.getStartDt() != null
                && request.getEndDt() != null
                && request.getEndDt().isBefore(request.getStartDt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "End date cannot be before the start date");
        }

        String employmentType = request.getEmpTyp()
                .trim().toUpperCase(Locale.ROOT);

        String employmentStatus = request.getEmpSts()
                .trim().toUpperCase(Locale.ROOT);

        if ("CURRENT".equals(employmentStatus)
                && request.getEndDt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A current employment record must not have an end date");
        }

        if ("UNEMPLOYED".equals(employmentType)
                && request.getMthInc() != null
                && request.getMthInc().signum() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Monthly income must be zero or omitted for an unemployed record");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CustomerEmployment employment = new CustomerEmployment();
        employment.setCustId(custId);
        employment.setEmpTyp(employmentType);
        employment.setEmpSts(employmentStatus);
        employment.setEmpNm(trimToNull(request.getEmpNm()));
        employment.setOcc(trimToNull(request.getOcc()));
        employment.setIndCd(trimToNull(request.getIndCd()));
        employment.setStartDt(request.getStartDt());
        employment.setEndDt(request.getEndDt());
        employment.setMthInc(request.getMthInc());

        String currency = request.getIncCcy();
        employment.setIncCcy(currency == null || currency.isBlank()
                ? "MYR"
                : currency.trim().toUpperCase(Locale.ROOT));

        employment.setCrtDt(now);
        employment.setCrtBy(CurrentActor.username());
        employment.setUpdDt(now);
        employment.setUpdBy(null);

        return employmentRepository.save(employment);
    }

    public List<CustomerEmployment> findByCustomer(Long custId) {
        Customer customer = customerRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));

        if (!"INDIVIDUAL".equalsIgnoreCase(customer.getCustTyp())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employment details are only available for individual customers");
        }

        return employmentRepository.findByCustId(custId);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Transactional
    public CustomerEmployment update(Long custId, Long recordId, CustomerEmploymentRequest request, org.springframework.security.core.Authentication authentication) {
        CustomerEmployment record = employmentRepository.findById(recordId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
        if (!customerRepository.existsById(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        if (!record.getCustId().equals(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found for customer");
        var customer = customerRepository.findById(custId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (!"INDIVIDUAL".equalsIgnoreCase(customer.getCustTyp()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong customer type");
        if (request.getStartDt() != null && request.getEndDt() != null && request.getEndDt().isBefore(request.getStartDt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date before start date");
        if ("CURRENT".equalsIgnoreCase(request.getEmpSts()) && request.getEndDt() != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current employment cannot have end date");
        record.setEmpTyp(request.getEmpTyp());
        record.setEmpSts(request.getEmpSts());
        record.setEmpNm(request.getEmpNm());
        record.setOcc(request.getOcc());
        record.setIndCd(request.getIndCd());
        record.setStartDt(request.getStartDt());
        record.setEndDt(request.getEndDt());
        record.setMthInc(request.getMthInc());
        record.setIncCcy(request.getIncCcy());
        record.setUpdDt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setUpdBy(CurrentActor.username());
        return employmentRepository.save(record);
    }
}

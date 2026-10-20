package com.cbms.service;

import com.cbms.dto.CustomerCreateRequest;
import com.cbms.entity.Customer;
import com.cbms.repository.CustomerRepository;
import com.cbms.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer createCustomer(CustomerCreateRequest request) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Customer customer = new Customer();
        customer.setCustNm(request.getCustNm().trim());
        customer.setCustTyp(request.getCustTyp());
        customer.setSts("PENDING");

        customer.setCrtDt(now);
        customer.setCrtBy(CurrentActor.username());
        customer.setUpdDt(now);
        customer.setUpdBy(null);

        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(Long custId) {
        return customerRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
}

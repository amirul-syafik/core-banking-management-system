package com.cbms.service;

import com.cbms.entity.BankAccount;
import com.cbms.entity.Customer;
import com.cbms.repository.CustomerRepository;
import org.springframework.stereotype.Component;

/**
 * Eligibility rules for NEW ordinary postings. Corrective reversals use their own policy.
 */
@Component
public class OrdinaryTransactionEligibility {
    private final CustomerRepository customerRepository;

    public OrdinaryTransactionEligibility(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public void requireActive(BankAccount account) {
        if (!"ACTIVE".equals(account.getAcctSts())) {
            throw new IllegalArgumentException("Only ACTIVE accounts can perform transactions");
        }
        Customer customer = customerRepository.findById(account.getCustId())
                .orElseThrow(() -> new IllegalStateException("Account customer not found"));
        if (!"ACTIVE".equals(customer.getSts())) {
            throw new IllegalArgumentException("Only ACTIVE customers can perform transactions");
        }
    }
}

package com.cbms.service;

import com.cbms.dto.CustomerContactRequest;
import com.cbms.entity.CustomerContact;
import com.cbms.repository.CustomerContactRepository;
import com.cbms.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerContactService {

    private final CustomerContactRepository contactRepository;
    private final CustomerRepository customerRepository;

    public CustomerContactService(
            CustomerContactRepository contactRepository,
            CustomerRepository customerRepository) {
        this.contactRepository = contactRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerContact addContact(
            Long custId,
            CustomerContactRequest request) {

        if (!customerRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        String contactType = request.getConTyp() == null
                ? null
                : request.getConTyp().trim().toUpperCase(Locale.ROOT);

        String contactValue = request.getConVal() == null
                ? null
                : request.getConVal().trim();

        if (contactType == null || contactType.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Contact type is required"
            );
        }

        if (contactValue == null || contactValue.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Contact value is required"
            );
        }

        boolean isPrimary = Boolean.TRUE.equals(request.getPriFlg());

        if (isPrimary) {
            List<CustomerContact> existingContacts =
                    contactRepository.findByCustId(custId);

            boolean primaryExists = existingContacts.stream()
                    .anyMatch(contact ->
                            Boolean.TRUE.equals(contact.getActFlg())
                                    && Boolean.TRUE.equals(contact.getPriFlg())
                                    && contactType.equals(contact.getConTyp()));

            if (primaryExists) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "An active primary contact of this type already exists"
                );
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CustomerContact contact = new CustomerContact();
        contact.setCustId(custId);
        contact.setConTyp(contactType);
        contact.setConVal(contactValue);
        contact.setPriFlg(isPrimary);
        contact.setActFlg(true);
        contact.setVfyDt(null);
        contact.setCrtDt(now);
        contact.setCrtBy("SYSTEM");
        contact.setUpdDt(now);
        contact.setUpdBy(null);

        return contactRepository.save(contact);
    }

    @Transactional(readOnly = true)
    public List<CustomerContact> getContacts(Long custId) {

        if (!customerRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        return contactRepository.findByCustId(custId);
    }
}
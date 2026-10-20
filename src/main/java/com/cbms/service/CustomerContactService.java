package com.cbms.service;

import com.cbms.dto.CustomerContactRequest;
import com.cbms.entity.CustomerContact;
import com.cbms.repository.CustomerContactRepository;
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
        contact.setCrtBy(CurrentActor.username());
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

    @Transactional
    public CustomerContact update(Long custId, Long recordId, CustomerContactRequest request, org.springframework.security.core.Authentication authentication) {
        CustomerContact record = contactRepository.findById(recordId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
        if (!customerRepository.existsById(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        if (!record.getCustId().equals(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found for customer");
        if (request.getConTyp() == null || request.getConTyp().isBlank() || request.getConVal() == null || request.getConVal().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact type and value required");
        if (Boolean.TRUE.equals(request.getPriFlg())) {
            boolean exists = contactRepository.findByCustId(custId).stream().anyMatch(c -> !c.getConId().equals(recordId) && Boolean.TRUE.equals(c.getActFlg()) && Boolean.TRUE.equals(c.getPriFlg()) && request.getConTyp().equalsIgnoreCase(c.getConTyp()));
            if (exists)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Primary contact of this type already exists");
        }
        record.setConTyp(request.getConTyp());
        record.setConVal(request.getConVal());
        record.setPriFlg(request.getPriFlg());
        record.setUpdDt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setUpdBy(CurrentActor.username());
        return contactRepository.save(record);
    }
}
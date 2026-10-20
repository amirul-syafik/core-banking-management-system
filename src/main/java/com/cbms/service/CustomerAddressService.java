package com.cbms.service;

import com.cbms.dto.CustomerAddressRequest;
import com.cbms.entity.CustomerAddress;
import com.cbms.repository.CustomerAddressRepository;
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
public class CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    public CustomerAddressService(
            CustomerAddressRepository addressRepository,
            CustomerRepository customerRepository) {
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerAddress addAddress(
            Long custId,
            CustomerAddressRequest request) {

        if (!customerRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        String addressType = request.getAdrTyp() == null
                ? null
                : request.getAdrTyp().trim().toUpperCase(Locale.ROOT);

        String countryCode = request.getCtyCd() == null
                ? null
                : request.getCtyCd().trim().toUpperCase(Locale.ROOT);

        boolean isPrimary = Boolean.TRUE.equals(request.getPriFlg());

        // Only one primary address is allowed per customer and address type.
        if (isPrimary && addressType != null) {
            List<CustomerAddress> existingAddresses =
                    addressRepository.findByCustIdOrderByAdrIdAsc(custId);

            boolean primaryExists = existingAddresses.stream()
                    .anyMatch(address ->
                            Boolean.TRUE.equals(address.getPriFlg())
                                    && addressType.equals(address.getAdrTyp()));

            if (primaryExists) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A primary address of this type already exists"
                );
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CustomerAddress address = new CustomerAddress();
        address.setCustId(custId);
        address.setAdrTyp(addressType);
        address.setAdrLn1(trimToNull(request.getAdrLn1()));
        address.setAdrLn2(trimToNull(request.getAdrLn2()));
        address.setAdrLn3(trimToNull(request.getAdrLn3()));
        address.setCity(trimToNull(request.getCity()));
        address.setStProv(trimToNull(request.getStProv()));
        address.setPstCd(trimToNull(request.getPstCd()));
        address.setCtyCd(countryCode);
        address.setPriFlg(isPrimary);
        address.setCrtDt(now);
        address.setCrtBy(CurrentActor.username());
        address.setUpdDt(now);
        address.setUpdBy(null);

        return addressRepository.save(address);
    }

    @Transactional(readOnly = true)
    public List<CustomerAddress> getAddresses(Long custId) {

        if (!customerRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        return addressRepository.findByCustIdOrderByAdrIdAsc(custId);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Transactional
    public CustomerAddress update(Long custId, Long recordId, CustomerAddressRequest request, org.springframework.security.core.Authentication authentication) {
        CustomerAddress record = addressRepository.findById(recordId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
        if (!customerRepository.existsById(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        if (!record.getCustId().equals(custId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found for customer");
        if (Boolean.TRUE.equals(request.getPriFlg())) {
            boolean exists = addressRepository.findByCustIdOrderByAdrIdAsc(custId).stream().anyMatch(a -> !a.getAdrId().equals(recordId) && Boolean.TRUE.equals(a.getPriFlg()) && request.getAdrTyp() != null && request.getAdrTyp().equalsIgnoreCase(a.getAdrTyp()));
            if (exists)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Primary address of this type already exists");
        }
        record.setAdrTyp(request.getAdrTyp());
        record.setAdrLn1(request.getAdrLn1());
        record.setAdrLn2(request.getAdrLn2());
        record.setAdrLn3(request.getAdrLn3());
        record.setCity(request.getCity());
        record.setStProv(request.getStProv());
        record.setPstCd(request.getPstCd());
        record.setCtyCd(request.getCtyCd());
        record.setPriFlg(request.getPriFlg());
        record.setUpdDt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setUpdBy(CurrentActor.username());
        return addressRepository.save(record);
    }
}
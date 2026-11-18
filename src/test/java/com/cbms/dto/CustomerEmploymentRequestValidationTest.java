package com.cbms.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CustomerEmploymentRequestValidationTest {
    private static Validator validator;
    private static jakarta.validation.ValidatorFactory factory;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void acceptsEmploymentDropdownValuesDefinedByDatabaseChecks() {
        Set<String> types = Set.of("FULL_TIME", "PART_TIME", "SELF_EMPLOYED", "CONTRACT", "UNEMPLOYED", "RETIRED", "OTHER");
        Set<String> statuses = Set.of("CURRENT", "PREVIOUS", "PENDING");

        for (String type : types) {
            for (String status : statuses) {
                CustomerEmploymentRequest request = request(type, status, BigDecimal.ZERO);
                assertTrue(validator.validate(request).isEmpty(), type + " / " + status);
            }
        }
    }

    @Test
    void rejectsEmploymentValuesOutsideDatabaseChecks() {
        CustomerEmploymentRequest request = request("TEMPORARY", "ACTIVE", BigDecimal.ZERO);
        Set<String> invalidFields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(invalidFields.contains("empTyp"));
        assertTrue(invalidFields.contains("empSts"));
    }

    private static CustomerEmploymentRequest request(String type, String status, BigDecimal income) {
        CustomerEmploymentRequest request = new CustomerEmploymentRequest();
        request.setEmpTyp(type);
        request.setEmpSts(status);
        request.setMthInc(income);
        request.setIncCcy("MYR");
        return request;
    }
}

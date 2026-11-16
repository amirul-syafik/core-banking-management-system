package com.cbms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Policy boundary regression checks; PostgreSQL integration is separately required. */
class BankFinancialCustomerEligibilityTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private static final Long CUSTOMER = 11L;

    @BeforeEach
    void setup() {
        reset(jdbc);
        when(jdbc.queryForList(contains("FROM public.customer_master"),
                eq(String.class), eq(CUSTOMER)))
                .thenReturn(List.of("ACTIVE"));
        when(jdbc.queryForList(contains("FROM public.customer_kyc"), eq(CUSTOMER)))
                .thenReturn(List.of(Map.of("asmt_sts", "VERIFIED", "kyc_current", true)));
        when(jdbc.queryForObject(contains("FROM public.customer_identification"),
                eq(Integer.class), eq(CUSTOMER))).thenReturn(1);
        when(jdbc.queryForList(contains("FROM public.bank_account"),
                eq(Long.class), eq(CUSTOMER))).thenReturn(List.of(25L));
        when(jdbc.queryForList(contains("FROM public.bank_account"),
                eq(Long.class), eq(25L), eq(CUSTOMER))).thenReturn(List.of(25L));
    }

    @Test
    void acceptsEligibleCustomerWithSuitableOwnedAccount() {
        assertDoesNotThrow(() -> BankFinancialCustomerEligibility.requireReadyForNewExposure(jdbc, CUSTOMER));
        assertDoesNotThrow(() -> BankFinancialCustomerEligibility.requireActiveOperatingAccount(jdbc, CUSTOMER));
        assertDoesNotThrow(() -> BankFinancialCustomerEligibility.requireOwnedOperatingAccount(jdbc, CUSTOMER, 25L));
    }

    @Test
    void blocksInactiveCustomer() {
        when(jdbc.queryForList(contains("FROM public.customer_master"),
                eq(String.class), eq(CUSTOMER))).thenReturn(List.of("SUSPENDED"));
        assertThrows(ResponseStatusException.class,
                () -> BankFinancialCustomerEligibility.requireReadyForNewExposure(jdbc, CUSTOMER));
    }

    @Test
    void rejectsUnverifiedOrExpiredLatestKycEvenIfOlderRowsWereVerified() {
        when(jdbc.queryForList(contains("FROM public.customer_kyc"), eq(CUSTOMER)))
                .thenReturn(List.of(Map.of("asmt_sts", "PENDING", "kyc_current", true)));
        assertThrows(ResponseStatusException.class,
                () -> BankFinancialCustomerEligibility.requireReadyForNewExposure(jdbc, CUSTOMER));
        when(jdbc.queryForList(contains("FROM public.customer_kyc"), eq(CUSTOMER)))
                .thenReturn(List.of(Map.of("asmt_sts", "VERIFIED", "kyc_current", false)));
        assertThrows(ResponseStatusException.class,
                () -> BankFinancialCustomerEligibility.requireReadyForNewExposure(jdbc, CUSTOMER));
    }

    @Test
    void rejectsMissingIdentification() {
        when(jdbc.queryForObject(contains("FROM public.customer_identification"),
                eq(Integer.class), eq(CUSTOMER))).thenReturn(0);
        assertThrows(ResponseStatusException.class,
                () -> BankFinancialCustomerEligibility.requireReadyForNewExposure(jdbc, CUSTOMER));
    }

    @Test
    void rejectsNoEligibleOperatingAccount() {
        when(jdbc.queryForList(contains("FROM public.bank_account"),
                eq(Long.class), eq(CUSTOMER))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class,
                () -> BankFinancialCustomerEligibility.requireActiveOperatingAccount(jdbc, CUSTOMER));
    }

    @Test
    void rejectsUnownedOrInactiveFundingAccount() {
        when(jdbc.queryForList(contains("FROM public.bank_account"),
                eq(Long.class), eq(25L), eq(CUSTOMER))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class,
                () -> BankFinancialCustomerEligibility.requireOwnedOperatingAccount(jdbc, CUSTOMER, 25L));
    }

    @Test
    void rejectsNullIdsBeforeDatabaseAccess() {
        assertThrows(ResponseStatusException.class,
                () -> BankFinancialCustomerEligibility.requireReadyForNewExposure(jdbc, null));
        assertThrows(ResponseStatusException.class,
                () -> BankFinancialCustomerEligibility.requireOwnedOperatingAccount(jdbc, CUSTOMER, null));
        verifyNoInteractions(jdbc);
    }
}

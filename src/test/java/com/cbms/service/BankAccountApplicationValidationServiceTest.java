package com.cbms.service;

import com.cbms.repository.BankAccountApplicationRepository;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankAccountApplicationValidationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BankAccountApplicationValidationService service = new BankAccountApplicationValidationService(
            mock(BankAccountApplicationRepository.class), mock(BankProductRepository.class),
            mock(BankSchemeRepository.class), jdbc);

    @Test
    @SuppressWarnings("unchecked")
    void rejectsWhenLatestAssessmentIsPendingEvenIfOlderAssessmentWasVerified() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(11L))).thenReturn(List.of(
                new BankAccountApplicationValidationService.KycRecord("PENDING", null, null)));
        var errors = new java.util.ArrayList<String>();

        service.validateLatestKyc(11L, errors, LocalDate.of(2026, 11, 18));

        assertEquals(List.of("Customer does not have a current VERIFIED KYC assessment"), errors);
        verify(jdbc).query(argThat(sql -> sql.contains("ORDER BY upd_dt DESC NULLS LAST, kyc_id DESC")
                        && sql.contains("LIMIT 1")), any(RowMapper.class), eq(11L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsOnlyLatestVerifiedAssessmentWithinReviewDates() {
        LocalDate today = LocalDate.of(2026, 11, 18);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(11L))).thenReturn(List.of(
                new BankAccountApplicationValidationService.KycRecord("VERIFIED", today.minusDays(1), today.plusDays(1))));
        var errors = new java.util.ArrayList<String>();

        service.validateLatestKyc(11L, errors, today);

        assertTrue(errors.isEmpty());
    }
}

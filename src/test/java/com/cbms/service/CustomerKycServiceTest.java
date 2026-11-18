package com.cbms.service;

import com.cbms.dto.CustomerKycRequest;
import com.cbms.entity.CustomerKyc;
import com.cbms.repository.CustomerKycRepository;
import com.cbms.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerKycServiceTest {
    private final CustomerKycRepository assessments = mock(CustomerKycRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerKycService service = new CustomerKycService(assessments, customers);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiedAssessmentRequiresDatesAcceptedByEligibilityRules() {
        when(customers.existsById(11L)).thenReturn(true);
        CustomerKycRequest request = request("VERIFIED", null, null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createAssessment(11L, request));

        assertEquals(400, error.getStatusCode().value());
        verify(assessments, never()).save(any());
    }

    @Test
    void updateNormalizesControlledValuesAndOrdersAssessmentHistoryLatestFirst() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("maker", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_MAKER"))));
        CustomerKyc record = new CustomerKyc();
        record.setKycId(3L);
        record.setCustId(11L);
        when(assessments.findById(3L)).thenReturn(Optional.of(record));
        when(customers.existsById(11L)).thenReturn(true);
        when(assessments.save(any(CustomerKyc.class))).thenAnswer(call -> call.getArgument(0));

        CustomerKycRequest request = request("VERIFIED", LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        request.setRiskRtng("LOW");
        request.setRmk(" reviewed ");

        CustomerKyc saved = service.update(11L, 3L, request, null);

        assertEquals("VERIFIED", saved.getAsmtSts());
        assertEquals("LOW", saved.getRiskRtng());
        assertEquals("reviewed", saved.getRmk());
        assertEquals(request.getRevDt(), saved.getRevDt());
        when(assessments.findByCustIdOrderByUpdDtDescKycIdDesc(11L)).thenReturn(List.of(saved));
        assertEquals(List.of(saved), service.getAssessments(11L));
        verify(assessments).findByCustIdOrderByUpdDtDescKycIdDesc(11L);
    }

    private static CustomerKycRequest request(String status, LocalDate reviewDate, LocalDate nextReviewDate) {
        CustomerKycRequest request = new CustomerKycRequest();
        request.setAsmtSts(status);
        request.setRiskRtng("LOW");
        request.setRevDt(reviewDate);
        request.setNxtRevDt(nextReviewDate);
        return request;
    }
}

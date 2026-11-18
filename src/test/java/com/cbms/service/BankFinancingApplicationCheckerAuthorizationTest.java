package com.cbms.service;

import com.cbms.entity.BankFinancingApplication;
import com.cbms.dto.BankFinancingApplicationValidationResponse;
import com.cbms.dto.BankFinancingRejectionRequest;
import com.cbms.repository.BankFinancingApplicationHistoryRepository;
import com.cbms.repository.BankFinancingApplicationRepository;
import com.cbms.repository.BankSchemeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BankFinancingApplicationCheckerAuthorizationTest {

    @Test
    void makerWithAdminRoleCannotApproveOrReject() {
        BankFinancingApplication application = new BankFinancingApplication();
        application.setApplId(11L);
        application.setApplSts("SUBMITTED");
        application.setCrtBy("another-maker");
        application.setSubmittedBy("another-maker");
        BankFinancingApplicationWorkflowService service = serviceFor(application);
        var makerAdmin = new UsernamePasswordAuthenticationToken("dual-role-user", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_MAKER")));

        ResponseStatusException approveError = assertThrows(ResponseStatusException.class,
                () -> service.approve(11L, null, makerAdmin));
        ResponseStatusException rejectError = assertThrows(ResponseStatusException.class,
                () -> service.reject(11L, null, makerAdmin));

        assertEquals(403, approveError.getStatusCode().value());
        assertEquals(403, rejectError.getStatusCode().value());
    }

    @Test
    void independentAdminMayReviewOwnCreatedApplicationWhenSomeoneElseSubmittedIt() {
        var checker = new UsernamePasswordAuthenticationToken("reviewer", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                serviceFor(submittedApplication("REVIEWER", "maker"))
                        .reject(11L, new BankFinancingRejectionRequest("Reviewed"), checker));
        ResponseStatusException submitterError = assertThrows(ResponseStatusException.class,
                () -> serviceFor(submittedApplication("maker", "REVIEWER"))
                        .reject(11L, null, checker));

        assertEquals(409, submitterError.getStatusCode().value());
    }

    private BankFinancingApplication submittedApplication(String creator, String submitter) {
        BankFinancingApplication application = new BankFinancingApplication();
        application.setApplId(11L);
        application.setApplSts("SUBMITTED");
        application.setCrtBy(creator);
        application.setSubmittedBy(submitter);
        return application;
    }

    private BankFinancingApplicationWorkflowService serviceFor(BankFinancingApplication application) {
        BankFinancingApplicationRepository applications = mock(BankFinancingApplicationRepository.class);
        when(applications.findByIdForUpdate(11L)).thenReturn(Optional.of(application));
        BankFinancingApplicationValidationService validation = mock(BankFinancingApplicationValidationService.class);
        when(validation.validate(11L)).thenReturn(new BankFinancingApplicationValidationResponse(
                11L, "FIN-TEST", true, List.of()));
        return new BankFinancingApplicationWorkflowService(
                applications,
                mock(BankFinancingApplicationHistoryRepository.class),
                validation,
                mock(BankSchemeRepository.class),
                mock(JdbcTemplate.class)
        );
    }
}

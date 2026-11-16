package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordRecoveryService service = new PasswordRecoveryService(jdbc);

    @Test void anonymousRequestIsAnInsertIfActiveWithConflictDeduplication() {
        service.requestAssistance("  maker  ");
        verify(jdbc).update(argThat((String sql) -> sql.contains("enabled IS TRUE")
                && sql.contains("ON CONFLICT DO NOTHING")
                && !sql.contains("RETURNING")), eq("maker"));
    }

    @Test void resolvingRequestIsOnlyAnAdministrativeFollowUp() {
        service.resolveAfterAdminReset(7L, "admin");
        verify(jdbc).update(contains("WHERE user_id=? AND status='OPEN'"), eq("admin"), eq(7L));
    }
}

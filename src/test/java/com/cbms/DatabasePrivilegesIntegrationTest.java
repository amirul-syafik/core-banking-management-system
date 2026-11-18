package com.cbms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Execute only after applying migration V20261114_19 to the PostgreSQL test database. */
@SpringBootTest
@ActiveProfiles("test")
class DatabasePrivilegesIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    private boolean tablePrivilege(String table, String privilege) {
        Boolean allowed = jdbc.queryForObject(
            "SELECT has_table_privilege('corebank_app', ?, ?)",
            Boolean.class, "public." + table, privilege);
        return Boolean.TRUE.equals(allowed);
    }

    private boolean columnPrivilege(String column, String privilege) {
        Boolean allowed = jdbc.queryForObject(
            "SELECT has_column_privilege('corebank_app', " +
                "'public.bank_islamic_execution_policy', ?, ?)",
            Boolean.class, column, privilege);
        return Boolean.TRUE.equals(allowed);
    }

    @Test
    void ledgersAndAuditRecordsCannotBeDeletedOrTruncatedByApp() {
        for (String table : new String[] {
            "bank_ledger_entry", "bank_transaction", "bank_transaction_reversal_audit",
            "application_activity_audit", "app_user_admin_audit",
            "bank_account_audit_history", "customer_audit_history", "bank_product_audit",
            "bank_scheme_audit", "bank_account_application_history",
            "bank_financing_application_history", "bank_system_parameter_history",
            "bank_credit_line_limit_history", "bank_credit_line_status_history",
            "bank_workflow_history"
        }) {
            assertThat(tablePrivilege(table, "DELETE")).as(table + " DELETE").isFalse();
            assertThat(tablePrivilege(table, "TRUNCATE")).as(table + " TRUNCATE").isFalse();
            assertThat(tablePrivilege(table, "TRIGGER")).as(table + " TRIGGER").isFalse();
            assertThat(tablePrivilege(table, "MAINTAIN")).as(table + " MAINTAIN").isFalse();
        }
    }

    @Test
    void permittedJournalOperationsRemainAvailable() {
        assertThat(tablePrivilege("bank_ledger_entry", "SELECT")).isTrue();
        assertThat(tablePrivilege("bank_ledger_entry", "INSERT")).isTrue();
        assertThat(tablePrivilege("bank_transaction", "UPDATE")).isTrue();
    }

    @Test
    void bankAttestationColumnsAreNeverWritableByApplication() {
        for (String column : new String[] {
            "bank_signoff_verified", "bank_signoff_verified_at", "bank_signoff_verified_by"
        }) {
            assertThat(columnPrivilege(column, "UPDATE")).as(column).isFalse();
        }
        for (String column : new String[] {
            "policy_sts", "submitted_by", "submitted_at",
            "decided_by", "decided_at", "review_ref", "review_remarks"
        }) {
            assertThat(columnPrivilege(column, "UPDATE")).as(column).isTrue();
        }
        assertThat(tablePrivilege("bank_islamic_execution_policy", "INSERT")).isTrue();
        assertThat(tablePrivilege("bank_islamic_execution_policy", "SELECT")).isTrue();
        assertThat(tablePrivilege("bank_islamic_execution_policy", "UPDATE")).isFalse();
    }

    @Test
    void trustedExternalReceiptsRemainReadOnly() {
        assertThat(tablePrivilege("bank_islamic_external_event_receipt", "SELECT")).isTrue();
        for (String privilege : new String[] {"INSERT", "UPDATE", "DELETE", "TRUNCATE", "TRIGGER", "MAINTAIN"}) {
            assertThat(tablePrivilege("bank_islamic_external_event_receipt", privilege)).as(privilege).isFalse();
        }
    }
}

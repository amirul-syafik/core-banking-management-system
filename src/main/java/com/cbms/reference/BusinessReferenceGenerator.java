package com.cbms.reference;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

/** Allocates display references from the sequences prepared for the CBMS reference migration. */
public final class BusinessReferenceGenerator {
    private BusinessReferenceGenerator() { }

    public enum Stream {
        ACCOUNT_APPLICATION("APP", "public.cbms_account_application_ref_seq"),
        CREDIT_LINE("CL", "public.cbms_credit_line_ref_seq"),
        COLLATERAL("COL", "public.cbms_collateral_ref_seq"),
        FINANCING_APPLICATION("FIN", "public.cbms_financing_application_ref_seq"),
        FINANCING_FACILITY("FAC", "public.cbms_financing_facility_ref_seq"),
        FIXED_DEPOSIT("FD", "public.cbms_fixed_deposit_ref_seq"),
        FD_ACCRUAL("FD-INT", "public.cbms_fd_accrual_ref_seq"),
        FD_PAYOUT("FD-PAY", "public.cbms_fd_payout_ref_seq"),
        FD_MATURITY("FD-MAT", "public.cbms_fd_maturity_ref_seq"),
        FD_PLACEMENT("FD-PLACE", "public.cbms_fd_placement_ref_seq"),
        FD_RENEWAL("FD-REN", "public.cbms_fd_renewal_ref_seq"),
        FD_PREMATURE_WITHDRAWAL("FD-PW", "public.cbms_fd_premature_withdrawal_ref_seq"),
        FINANCING_DISBURSEMENT("DISB", "public.cbms_financing_disbursement_ref_seq"),
        FINANCING_CLOSURE("CLOSE", "public.cbms_financing_closure_ref_seq"),
        FINANCING_REPAYMENT("REPAY", "public.cbms_financing_repayment_ref_seq"),
        FLOOR_STOCK("FS", "public.cbms_floor_stock_ref_seq"),
        GL_CHANGE_REQUEST("GLM", "public.cbms_gl_change_request_ref_seq"),
        ISLAMIC_EXECUTION_POLICY("IEP", "public.cbms_islamic_execution_policy_ref_seq"),
        ISLAMIC_EXECUTION_EVIDENCE("IER", "public.cbms_islamic_execution_evidence_ref_seq"),
        ISLAMIC_EXECUTION_TIMELINE("ITL", "public.cbms_islamic_execution_timeline_ref_seq"),
        ISLAMIC_LEDGER_MAPPING("ILM", "public.cbms_islamic_ledger_mapping_ref_seq"),
        TRANSACTION("TXN", "public.cbms_transaction_ref_seq"),
        TRANSACTION_REVERSAL("REV", "public.cbms_transaction_reversal_ref_seq"),
        WORKFLOW_INSTANCE("WFI", "public.cbms_workflow_instance_ref_seq"),
        WORKFLOW_TASK("WFT", "public.cbms_workflow_task_ref_seq");

        private final String prefix;
        private final String sequenceName;

        Stream(String prefix, String sequenceName) {
            this.prefix = prefix;
            this.sequenceName = sequenceName;
        }
    }

    public static String next(JdbcTemplate jdbc, Stream stream) {
        return next(jdbc, stream, null);
    }

    public static String next(JdbcTemplate jdbc, Stream stream, OffsetDateTime createdAt) {
        Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(stream, "stream");
        String yearExpression = createdAt == null
                ? "EXTRACT(YEAR FROM CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kuala_Lumpur')::int"
                : "EXTRACT(YEAR FROM CAST(? AS timestamptz) AT TIME ZONE 'Asia/Kuala_Lumpur')::int";
        Map<String, Object> allocation = jdbc.queryForMap(
                "SELECT nextval(CAST(? AS regclass)) AS sequence_value, " + yearExpression
                        + " AS reference_year",
                createdAt == null
                        ? new Object[]{stream.sequenceName}
                        : new Object[]{stream.sequenceName, createdAt}
        );
        long sequenceValue = ((Number) allocation.get("sequence_value")).longValue();
        int referenceYear = ((Number) allocation.get("reference_year")).intValue();
        if (sequenceValue < 1 || referenceYear < 1) {
            throw new IllegalStateException("The database did not allocate a business reference sequence value");
        }
        return String.format(Locale.ROOT, "%s-%04d-%06d", stream.prefix,
                referenceYear, sequenceValue);
    }
}

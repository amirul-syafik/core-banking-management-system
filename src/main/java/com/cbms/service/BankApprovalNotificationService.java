package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class BankApprovalNotificationService {
    /**
     * Do not expose stale role-fanout tasks once another checker decided/claimed them.
     * The same live check controls Open Request and the inbox/unread count.
     * Outcome and informational messages remain visible to their intended recipients.
     */
    private static final String ACTIONABLE = """
        CASE
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'CUSTOMER_STATUS' THEN EXISTS (
            SELECT 1 FROM public.customer_status_request r WHERE r.request_id = n.source_id AND r.request_status = 'PENDING')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'ACCOUNT_STATUS' THEN EXISTS (
            SELECT 1 FROM public.bank_account_status_request r WHERE r.request_id = n.source_id AND r.request_status = 'PENDING')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'ACCOUNT_APPLICATION' THEN EXISTS (
            SELECT 1 FROM public.bank_account_application r WHERE r.appl_id = n.source_id AND r.appl_sts = 'SUBMITTED')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'FINANCING_APPLICATION' THEN EXISTS (
            SELECT 1 FROM public.bank_financing_application r WHERE r.appl_id = n.source_id AND r.appl_sts = 'SUBMITTED')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'REVERSAL_REQUEST' THEN EXISTS (
            SELECT 1 FROM public.bank_transaction_reversal_request r WHERE r.request_id = n.source_id AND r.status = 'PENDING')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'ISLAMIC_CONTRACT' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_contract_profile p
            WHERE p.profile_id = n.source_id AND p.profile_sts = 'SUBMITTED')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'FD_ISLAMIC_TERMS' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_deposit_profit_terms t
            WHERE t.terms_id = n.source_id AND t.terms_sts = 'SUBMITTED')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'FD_ISLAMIC_EXECUTION' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_deposit_execution_review e
            WHERE e.execution_id = n.source_id AND e.review_sts = 'SUBMITTED')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'FD_ISLAMIC_TIMELINE' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_deposit_execution_timeline t
            WHERE t.timeline_id = n.source_id AND t.review_sts = 'SUBMITTED')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'FD_ISLAMIC_LEDGER_MAPPING' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_deposit_ledger_mapping_review m
            WHERE m.mapping_id = n.source_id AND m.mapping_sts = 'SUBMITTED')
          WHEN n.event_cd = 'PENDING' AND n.source_type = 'FD_APPROVAL' THEN EXISTS (
            SELECT 1 FROM public.bank_fixed_deposit_approval_request r
            WHERE r.request_id = n.source_id AND r.request_status = 'PENDING'
              AND (r.assigned_checker IS NULL OR LOWER(r.assigned_checker) = LOWER(n.recipient_username)))
          WHEN n.event_cd LIKE 'ASSIGNED_%' AND n.source_type = 'FD_APPROVAL' THEN EXISTS (
            SELECT 1 FROM public.bank_fixed_deposit_approval_request r
            WHERE r.request_id = n.source_id AND r.request_status = 'PENDING'
              AND LOWER(r.assigned_checker) = LOWER(n.recipient_username))
          WHEN n.event_cd = 'QUEUED' AND n.source_type = 'WORKFLOW_TASK' THEN EXISTS (
            SELECT 1 FROM public.bank_workflow_task t WHERE t.task_id = n.source_id
              AND ((t.task_sts = 'PENDING' AND t.assigned_to IS NULL)
                   OR (t.task_sts = 'CLAIMED' AND LOWER(t.assigned_to) = LOWER(n.recipient_username))))
          WHEN n.event_cd = 'ASSIGNED' AND n.source_type = 'WORKFLOW_TASK' THEN EXISTS (
            SELECT 1 FROM public.bank_workflow_task t WHERE t.task_id = n.source_id
              AND t.task_sts IN ('PENDING', 'CLAIMED')
              AND LOWER(t.assigned_to) = LOWER(n.recipient_username))
          ELSE FALSE
        END
        """;

    /** Keep the completed notification only for the checker who actually acted. */
    private static final String COMPLETED_BY_RECIPIENT = """
        CASE
          WHEN n.source_type = 'CUSTOMER_STATUS' THEN EXISTS (
            SELECT 1 FROM public.customer_status_request r WHERE r.request_id=n.source_id
              AND r.request_status <> 'PENDING' AND LOWER(r.reviewed_by)=LOWER(n.recipient_username))
          WHEN n.source_type = 'ACCOUNT_STATUS' THEN EXISTS (
            SELECT 1 FROM public.bank_account_status_request r WHERE r.request_id=n.source_id
              AND r.request_status <> 'PENDING' AND LOWER(r.reviewed_by)=LOWER(n.recipient_username))
          WHEN n.source_type = 'ACCOUNT_APPLICATION' THEN EXISTS (
            SELECT 1 FROM public.bank_account_application r WHERE r.appl_id=n.source_id
              AND r.appl_sts NOT IN ('DRAFT','SUBMITTED') AND LOWER(r.reviewed_by)=LOWER(n.recipient_username))
          WHEN n.source_type = 'FINANCING_APPLICATION' THEN EXISTS (
            SELECT 1 FROM public.bank_financing_application r WHERE r.appl_id=n.source_id
              AND r.appl_sts NOT IN ('DRAFT','SUBMITTED') AND LOWER(r.reviewed_by)=LOWER(n.recipient_username))
          WHEN n.source_type = 'REVERSAL_REQUEST' THEN EXISTS (
            SELECT 1 FROM public.bank_transaction_reversal_request r WHERE r.request_id=n.source_id
              AND r.status <> 'PENDING' AND LOWER(r.decided_by)=LOWER(n.recipient_username))
          WHEN n.source_type = 'ISLAMIC_CONTRACT' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_contract_profile p WHERE p.profile_id=n.source_id
              AND p.profile_sts IN ('APPROVED','REJECTED') AND LOWER(p.decided_by)=LOWER(n.recipient_username))
          WHEN n.source_type = 'FD_ISLAMIC_TERMS' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_deposit_profit_terms t
            WHERE t.terms_id = n.source_id AND t.terms_sts IN ('APPROVED','REJECTED')
              AND LOWER(t.decided_by) = LOWER(n.recipient_username))
          WHEN n.source_type = 'FD_ISLAMIC_EXECUTION' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_deposit_execution_review e WHERE e.execution_id = n.source_id
              AND e.review_sts IN ('REVIEWED','REJECTED')
              AND LOWER(e.decided_by) = LOWER(n.recipient_username))
          WHEN n.source_type = 'FD_ISLAMIC_TIMELINE' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_deposit_execution_timeline t WHERE t.timeline_id = n.source_id
              AND t.review_sts IN ('DOCUMENT_REVIEWED','REJECTED')
              AND LOWER(t.decided_by) = LOWER(n.recipient_username))
          WHEN n.source_type = 'FD_ISLAMIC_LEDGER_MAPPING' THEN EXISTS (
            SELECT 1 FROM public.bank_islamic_deposit_ledger_mapping_review m WHERE m.mapping_id = n.source_id
              AND m.mapping_sts IN ('REVIEWED','REJECTED')
              AND LOWER(m.decided_by) = LOWER(n.recipient_username))
          WHEN n.source_type = 'FD_APPROVAL' THEN EXISTS (
            SELECT 1 FROM public.bank_fixed_deposit_approval_request r WHERE r.request_id=n.source_id
              AND r.request_status <> 'PENDING' AND LOWER(r.decided_by)=LOWER(n.recipient_username))
          WHEN n.source_type = 'WORKFLOW_TASK' THEN EXISTS (
            SELECT 1 FROM public.bank_workflow_task t WHERE t.task_id=n.source_id
              AND t.task_sts NOT IN ('PENDING','CLAIMED') AND LOWER(t.action_by)=LOWER(n.recipient_username))
          ELSE FALSE
        END
        """;

    private static final String VISIBLE_CTE = """
        WITH notification_state AS (
          SELECT n.notification_id, (%s) AS action_available,
                 (%s) AS completed_by_recipient
          FROM public.bank_user_notification n
          WHERE LOWER(n.recipient_username) = LOWER(?)
        )
        """.formatted(ACTIONABLE, COMPLETED_BY_RECIPIENT);

    private static final String VISIBLE_FILTER = """
        AND (s.action_available = TRUE OR
             (s.completed_by_recipient = TRUE AND
              (n.event_cd IN ('PENDING','QUEUED','ASSIGNED') OR n.event_cd LIKE 'ASSIGNED_%')) OR
             (n.event_cd NOT IN ('PENDING','QUEUED','ASSIGNED')
              AND n.event_cd NOT LIKE 'ASSIGNED_%'))
        """;

    private final JdbcTemplate jdbc;

    public BankApprovalNotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Fan out to enabled users with this role; do not notify the maker about their own request. */
    @Transactional
    public void notifyRole(String role, String excludeUsername, String module,
                           String sourceType, long sourceId, String event,
                           String subject, String message) {
        jdbc.update("""
            INSERT INTO public.bank_user_notification
                (recipient_username, module_cd, source_type, source_id, event_cd, subject, message)
            SELECT DISTINCT u.username, ?, ?, ?, ?, ?, ?
            FROM public.app_user u
            JOIN public.app_user_role ur ON ur.user_id = u.user_id
            JOIN public.app_role r ON r.role_id = ur.role_id
            WHERE u.enabled = TRUE AND r.role_nm = ?
              AND LOWER(u.username) <> LOWER(COALESCE(?, ''))
            ON CONFLICT (recipient_username, source_type, source_id, event_cd) DO NOTHING
            """, module, sourceType, sourceId, event, subject, message,
                role, excludeUsername);
    }

    /** Only existing, enabled users are eligible for individual notifications. */
    @Transactional
    public void notifyUser(String recipient, String module, String sourceType,
                           long sourceId, String event, String subject, String message) {
        if (recipient == null || recipient.isBlank()) return;
        jdbc.update("""
            INSERT INTO public.bank_user_notification
                (recipient_username, module_cd, source_type, source_id, event_cd, subject, message)
            SELECT username, ?, ?, ?, ?, ?, ? FROM public.app_user
            WHERE LOWER(username) = LOWER(?) AND enabled = TRUE
            ON CONFLICT (recipient_username, source_type, source_id, event_cd) DO NOTHING
            """, module, sourceType, sourceId, event, subject, message, recipient);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(Authentication authentication, boolean unreadOnly, int limit) {
        String recipient = authenticatedUser(authentication);
        return jdbc.queryForList(VISIBLE_CTE + """
            SELECT n.notification_id AS "notificationId", n.module_cd AS "moduleCode",
                   n.source_type AS "sourceType", n.source_id AS "sourceId", n.event_cd AS "eventCode",
                   n.subject, n.message, n.created_at AS "createdAt", n.read_at AS "readAt",
                   s.action_available AS "actionAvailable",
                   s.completed_by_recipient AS "completedByCurrentUser",
                   CASE n.source_type
                     WHEN 'CUSTOMER_STATUS' THEN (SELECT s.cust_id FROM public.customer_status_request s WHERE s.request_id = n.source_id)
                     WHEN 'ACCOUNT_STATUS' THEN (SELECT s.acct_id FROM public.bank_account_status_request s WHERE s.request_id = n.source_id)
                     WHEN 'ISLAMIC_CONTRACT' THEN (SELECT p.schm_id FROM public.bank_islamic_contract_profile p WHERE p.profile_id = n.source_id)
                     WHEN 'FD_ISLAMIC_TERMS' THEN (SELECT t.fd_id FROM public.bank_islamic_deposit_profit_terms t WHERE t.terms_id = n.source_id)
                     WHEN 'FD_ISLAMIC_EXECUTION' THEN (SELECT e.fd_id FROM public.bank_islamic_deposit_execution_review e WHERE e.execution_id = n.source_id)
                     WHEN 'FD_ISLAMIC_TIMELINE' THEN (SELECT t.fd_id FROM public.bank_islamic_deposit_execution_timeline t WHERE t.timeline_id = n.source_id)
                     WHEN 'FD_ISLAMIC_LEDGER_MAPPING' THEN (SELECT m.fd_id FROM public.bank_islamic_deposit_ledger_mapping_review m WHERE m.mapping_id = n.source_id)
                     WHEN 'FD_APPROVAL' THEN (SELECT s.fd_id FROM public.bank_fixed_deposit_approval_request s WHERE s.request_id = n.source_id)
                     WHEN 'WORKFLOW_TASK' THEN (SELECT i.entity_id FROM public.bank_workflow_task t
                         JOIN public.bank_workflow_instance i ON i.instance_id = t.instance_id WHERE t.task_id = n.source_id)
                     ELSE n.source_id END AS "targetId",
                   CASE n.source_type
                     WHEN 'CUSTOMER_STATUS' THEN (SELECT c.cif_no || ' · ' || c.cust_nm
                         FROM public.customer_status_request s JOIN public.customer_master c ON c.cust_id = s.cust_id
                         WHERE s.request_id = n.source_id)
                     WHEN 'ACCOUNT_STATUS' THEN (SELECT a.acct_no || ' · ' || c.cust_nm
                         FROM public.bank_account_status_request s JOIN public.bank_account a ON a.acct_id = s.acct_id
                         JOIN public.customer_master c ON c.cust_id = a.cust_id WHERE s.request_id = n.source_id)
                     WHEN 'ISLAMIC_CONTRACT' THEN (SELECT s.schm_cd || ' · ' || s.schm_nm
                         FROM public.bank_islamic_contract_profile p
                         JOIN public.bank_scheme s ON s.schm_id = p.schm_id WHERE p.profile_id = n.source_id)
                     WHEN 'FD_ISLAMIC_TERMS' THEN (SELECT fd.fd_ref FROM public.bank_islamic_deposit_profit_terms t
                         JOIN public.bank_fixed_deposit fd ON fd.fd_id = t.fd_id WHERE t.terms_id = n.source_id)
                     WHEN 'FD_ISLAMIC_EXECUTION' THEN (SELECT fd.fd_ref FROM public.bank_islamic_deposit_execution_review e
                         JOIN public.bank_fixed_deposit fd ON fd.fd_id = e.fd_id WHERE e.execution_id = n.source_id)
                     WHEN 'FD_ISLAMIC_TIMELINE' THEN (SELECT fd.fd_ref FROM public.bank_islamic_deposit_execution_timeline t
                         JOIN public.bank_fixed_deposit fd ON fd.fd_id = t.fd_id WHERE t.timeline_id = n.source_id)
                     WHEN 'FD_ISLAMIC_LEDGER_MAPPING' THEN (SELECT fd.fd_ref FROM public.bank_islamic_deposit_ledger_mapping_review m
                         JOIN public.bank_fixed_deposit fd ON fd.fd_id = m.fd_id WHERE m.mapping_id = n.source_id)
                     WHEN 'FD_APPROVAL' THEN (SELECT fd.fd_ref FROM public.bank_fixed_deposit_approval_request s
                         JOIN public.bank_fixed_deposit fd ON fd.fd_id = s.fd_id WHERE s.request_id = n.source_id)
                     WHEN 'ACCOUNT_APPLICATION' THEN (SELECT a.appl_ref || ' · ' || c.cust_nm
                         FROM public.bank_account_application a JOIN public.customer_master c ON c.cust_id = a.cust_id
                         WHERE a.appl_id = n.source_id)
                     WHEN 'FINANCING_APPLICATION' THEN (SELECT a.appl_ref || ' · ' || c.cust_nm
                         FROM public.bank_financing_application a JOIN public.customer_master c ON c.cust_id = a.cust_id
                         WHERE a.appl_id = n.source_id)
                     WHEN 'REVERSAL_REQUEST' THEN (SELECT t.txn_ref FROM public.bank_transaction_reversal_request r
                         JOIN public.bank_transaction t ON t.txn_id = r.original_txn_id WHERE r.request_id = n.source_id)
                     WHEN 'WORKFLOW_TASK' THEN (SELECT COALESCE(i.entity_ref, i.instance_ref)
                         FROM public.bank_workflow_task t JOIN public.bank_workflow_instance i ON i.instance_id = t.instance_id
                         WHERE t.task_id = n.source_id)
                     ELSE NULL END AS "targetReference"
            FROM public.bank_user_notification n
            JOIN notification_state s ON s.notification_id = n.notification_id
            WHERE (? = FALSE OR n.read_at IS NULL)
            """ + VISIBLE_FILTER + """
            ORDER BY n.created_at DESC, n.notification_id DESC LIMIT ?
            """, recipient, unreadOnly, Math.min(100, Math.max(1, limit)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Authentication authentication) {
        Long count = jdbc.queryForObject(VISIBLE_CTE + """
            SELECT COUNT(*) FROM public.bank_user_notification n
            JOIN notification_state s ON s.notification_id = n.notification_id
            WHERE n.read_at IS NULL
            """ + VISIBLE_FILTER, Long.class, authenticatedUser(authentication));
        return count == null ? 0L : count;
    }

    @Transactional
    public void markRead(long notificationId, Authentication authentication) {
        if (notificationId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification");
        int updated = jdbc.update("""
            UPDATE public.bank_user_notification SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
            WHERE notification_id = ? AND LOWER(recipient_username) = LOWER(?)
            """, notificationId, authenticatedUser(authentication));
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
    }

    @Transactional
    public int markAllRead(Authentication authentication) {
        return jdbc.update("""
            UPDATE public.bank_user_notification SET read_at = CURRENT_TIMESTAMP
            WHERE LOWER(recipient_username) = LOWER(?) AND read_at IS NULL
            """, authenticatedUser(authentication));
    }

    private String authenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        return auth.getName();
    }
}

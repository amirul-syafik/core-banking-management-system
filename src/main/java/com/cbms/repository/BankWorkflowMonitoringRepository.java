package com.cbms.repository;

import com.cbms.dto.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class BankWorkflowMonitoringRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public BankWorkflowMonitoringRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private long count(String sql, MapSqlParameterSource args) {
        Long result = jdbc.queryForObject(sql, args, Long.class);
        return result == null ? 0L : result;
    }

    public BankWorkflowMonitoringSummaryResponse summary(int hours, OffsetDateTime cutoff) {
        var p = new MapSqlParameterSource("cutoff", cutoff);
        String instances = "FROM public.bank_workflow_instance";
        String tasks = "FROM public.bank_workflow_task";
        long total = count("SELECT count(*) " + instances, p);
        long pending = count("SELECT count(*) " + instances + " WHERE workflow_sts='PENDING'", p);
        long progressing = count("SELECT count(*) " + instances + " WHERE workflow_sts='IN_PROGRESS'", p);
        long approved = count("SELECT count(*) " + instances + " WHERE workflow_sts='APPROVED'", p);
        long rejected = count("SELECT count(*) " + instances + " WHERE workflow_sts='REJECTED'", p);
        long cancelled = count("SELECT count(*) " + instances + " WHERE workflow_sts='CANCELLED'", p);
        long pendingTasks = count("SELECT count(*) " + tasks + " WHERE task_sts='PENDING'", p);
        long claimedTasks = count("SELECT count(*) " + tasks + " WHERE task_sts='CLAIMED'", p);
        long overdue = count("SELECT count(*) " + tasks + " WHERE task_sts IN ('PENDING','CLAIMED') AND assigned_dt < :cutoff", p);
        return new BankWorkflowMonitoringSummaryResponse(total, pending, progressing,
                approved, rejected, cancelled, pendingTasks + claimedTasks,
                pendingTasks, claimedTasks, overdue, hours);
    }

    private static final String TASK_SELECT = """
            SELECT t.task_id,t.task_ref,t.instance_id,i.instance_ref,
                   w.workflow_id,w.workflow_cd,w.module_cd,i.entity_id,i.entity_ref,
                   s.step_no,s.step_nm,t.assigned_role,t.assigned_to,t.task_sts,
                   t.assigned_dt,t.claimed_dt,
                   GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - t.assigned_dt)) / 3600))::bigint AS age_hours
              FROM public.bank_workflow_task t
              JOIN public.bank_workflow_instance i ON i.instance_id=t.instance_id
              JOIN public.bank_workflow_definition w ON w.workflow_id=i.workflow_id
              JOIN public.bank_workflow_step s ON s.step_id=t.step_id
            """;

    private BankWorkflowMonitoringTaskResponse mapTask(ResultSet r, int rowNum, OffsetDateTime cutoff) throws SQLException {
        OffsetDateTime assigned = r.getObject("assigned_dt", OffsetDateTime.class);
        OffsetDateTime claimed = r.getObject("claimed_dt", OffsetDateTime.class);
        return new BankWorkflowMonitoringTaskResponse(
                r.getLong("task_id"), r.getString("task_ref"), r.getLong("instance_id"), r.getString("instance_ref"),
                r.getLong("workflow_id"), r.getString("workflow_cd"), r.getString("module_cd"),
                r.getLong("entity_id"), r.getString("entity_ref"), r.getInt("step_no"), r.getString("step_nm"),
                r.getString("assigned_role"), r.getString("assigned_to"), r.getString("task_sts"),
                assigned, claimed, r.getLong("age_hours"), assigned != null && assigned.isBefore(cutoff));
    }

    public BankWorkflowMonitoringTaskPageResponse tasks(String role, boolean overdueOnly,
                                                        int hours, OffsetDateTime cutoff, int page, int size) {
        var p = new MapSqlParameterSource().addValue("cutoff", cutoff)
                .addValue("limit", size).addValue("offset", (long) page * size);
        StringBuilder where = new StringBuilder(" WHERE t.task_sts IN ('PENDING','CLAIMED')");
        if (role != null) {
            where.append(" AND t.assigned_role=:role");
            p.addValue("role", role);
        }
        if (overdueOnly) {
            where.append(" AND t.assigned_dt < :cutoff");
        }
        String joins = " FROM public.bank_workflow_task t " +
                "JOIN public.bank_workflow_instance i ON i.instance_id=t.instance_id " +
                "JOIN public.bank_workflow_definition w ON w.workflow_id=i.workflow_id " +
                "JOIN public.bank_workflow_step s ON s.step_id=t.step_id ";
        long total = count("SELECT count(*) " + joins + where, p);
        String sql = TASK_SELECT + where + " ORDER BY t.assigned_dt ASC,t.task_id ASC LIMIT :limit OFFSET :offset";
        List<BankWorkflowMonitoringTaskResponse> content = jdbc.query(sql, p,
                (rs, rowNum) -> mapTask(rs, rowNum, cutoff));
        return new BankWorkflowMonitoringTaskPageResponse(content, total, page, size,
                (total + size - 1) / size);
    }

    public BankWorkflowMonitoringStatisticsResponse statistics() {
        var p = new MapSqlParameterSource();
        long completed = count("SELECT count(*) FROM public.bank_workflow_instance WHERE workflow_sts IN ('APPROVED','REJECTED')", p);
        long approved = count("SELECT count(*) FROM public.bank_workflow_instance WHERE workflow_sts='APPROVED'", p);
        long rejected = count("SELECT count(*) FROM public.bank_workflow_instance WHERE workflow_sts='REJECTED'", p);
        long approvedTasks = count("SELECT count(*) FROM public.bank_workflow_task WHERE task_sts='APPROVED'", p);
        long rejectedTasks = count("SELECT count(*) FROM public.bank_workflow_task WHERE task_sts='REJECTED'", p);
        Double avg = jdbc.queryForObject("""
                SELECT AVG(EXTRACT(EPOCH FROM (completed_dt - submitted_dt)) / 3600.0)
                FROM public.bank_workflow_instance
                WHERE workflow_sts IN ('APPROVED','REJECTED') AND completed_dt IS NOT NULL
                """, p, Double.class);
        return new BankWorkflowMonitoringStatisticsResponse(completed, approved, rejected,
                approvedTasks + rejectedTasks, approvedTasks, rejectedTasks, avg);
    }
}

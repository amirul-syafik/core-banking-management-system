package com.cbms.service;

import com.cbms.dto.BankFixedDepositMaturitySettlementRequest;
import com.cbms.dto.BankFixedDepositPrematureWithdrawalRequest;
import com.cbms.dto.BankFixedDepositRenewalRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BankFixedDepositApprovalService {
    private static final Set<String> ACTIONS = Set.of("MATURITY_SETTLEMENT", "RENEWAL", "PREMATURE_UPLIFT");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BankFixedDepositMaturitySettlementService maturity;
    private final BankFixedDepositRenewalService renewal;
    private final BankFixedDepositPrematureWithdrawalService uplift;
    private BankApprovalNotificationService notifications;

    @Autowired
    public void setNotificationService(BankApprovalNotificationService notifications) {
        this.notifications = notifications;
    }

    public BankFixedDepositApprovalService(JdbcTemplate jdbc, ObjectMapper mapper,
             BankFixedDepositMaturitySettlementService maturity, BankFixedDepositRenewalService renewal,
             BankFixedDepositPrematureWithdrawalService uplift) {
        this.jdbc=jdbc;this.mapper=mapper;this.maturity=maturity;this.renewal=renewal;this.uplift=uplift;
    }
    private ResponseStatusException error(HttpStatus status, String text) {
        return new ResponseStatusException(status,text);
    }
    private String actor(Authentication user) {
        if (user==null || !user.isAuthenticated() || user.getName()==null || user.getName().isBlank())
            throw error(HttpStatus.UNAUTHORIZED,"Authentication required");
        return user.getName();
    }
    private void checkLength(String value, String field, boolean required) {
        if ((required && (value==null || value.isBlank())) || (value!=null && value.length()>500))
            throw error(HttpStatus.BAD_REQUEST, field+" must be present and at most 500 characters");
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(JacksonException e) {throw error(HttpStatus.BAD_REQUEST,"Invalid request payload");}
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> parse(String value) {
        try {return mapper.readValue(value,Map.class);}
        catch(JacksonException e) {throw error(HttpStatus.CONFLICT,"Stored request payload is invalid");}
    }
    private long positive(Object value,String label) {
        if (!(value instanceof Number n) || n.longValue()<=0 || n.doubleValue()!=n.longValue())
            throw error(HttpStatus.BAD_REQUEST,label+" must be a positive integer");
        return n.longValue();
    }
    private BankFixedDepositRenewalRequest renewDto(Map<String,Object> payload) {
        Object type=payload.get("renewalType");
        if (!(type instanceof String t) || !(t.equals("PRINCIPAL_ONLY")||t.equals("PRINCIPAL_PLUS_INTEREST")))
            throw error(HttpStatus.BAD_REQUEST,"Invalid renewal type");
        Object months=payload.get("tenorMonths");
        int tenor=(int)positive(months,"Tenor");
        if (tenor>600)throw error(HttpStatus.BAD_REQUEST,"Tenor must be <= 600");
        String remarks=payload.get("remarks") instanceof String r?r:null;
        if (remarks!=null && remarks.length()>500)throw error(HttpStatus.BAD_REQUEST,"Remarks are too long");
        Object interest = payload.get("interestRate");
        java.math.BigDecimal selectedRate = null;
        if (interest != null) {
            try { selectedRate = new java.math.BigDecimal(String.valueOf(interest)); }
            catch (NumberFormatException exception) {
                throw error(HttpStatus.BAD_REQUEST, "Invalid selected renewal interest rate");
            }
            if (selectedRate.signum() < 0 || selectedRate.compareTo(new java.math.BigDecimal("100")) > 0)
                throw error(HttpStatus.BAD_REQUEST, "Invalid selected renewal interest rate");
        }
        return new BankFixedDepositRenewalRequest(t,positive(payload.get("schemeId"),"Scheme"),tenor,selectedRate,remarks);
    }
    private long destination(Map<String,Object> payload) {
        return positive(payload.get("destinationAccountId"),"Destination account");
    }
    private void validatePayload(String action,Map<String,Object> payload) {
        if (payload==null)throw error(HttpStatus.BAD_REQUEST,"Payload required");
        if (action.equals("RENEWAL"))renewDto(payload);
        else destination(payload);
        if (!action.equals("RENEWAL") && payload.get("remarks")!=null &&
            (!(payload.get("remarks") instanceof String) || ((String)payload.get("remarks")).length()>500))
            throw error(HttpStatus.BAD_REQUEST,"Invalid remarks");
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(int limit) {
        return jdbc.queryForList("""
           SELECT request_id,fd_id,action_type,request_reason,request_status,requested_by,requested_at,
                  assigned_checker,assigned_by,assigned_at,decided_by,decided_at,decision_reason,posting_result::text AS posting_result
           FROM public.bank_fixed_deposit_approval_request ORDER BY requested_at DESC, request_id DESC LIMIT ?
           """,Math.min(100,Math.max(1,limit)));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> get(Long requestId) {
        var rows = jdbc.queryForList("""
            SELECT r.request_id,r.fd_id,r.action_type,r.request_reason,r.request_status,r.requested_by,r.requested_at,
                   r.assigned_checker,r.assigned_by,r.assigned_at,r.decided_by,r.decided_at,r.decision_reason,
                   r.posting_result::text AS posting_result, fd.fd_ref AS fd_reference
            FROM public.bank_fixed_deposit_approval_request r
            LEFT JOIN public.bank_fixed_deposit fd ON fd.fd_id = r.fd_id WHERE r.request_id = ?
            """, requestId);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Approval request not found");
        return rows.get(0);
    }
    @Transactional
    public Map<String,Object> submit(Long fdId,String action,Map<String,Object> payload,String reason,Authentication user) {
        String maker=actor(user);
        if (fdId==null || fdId<=0 || action==null || !ACTIONS.contains(action))
            throw error(HttpStatus.BAD_REQUEST,"Invalid FD/action");
        checkLength(reason,"Request reason",true);
        validatePayload(action,payload);
        var state=jdbc.queryForList("SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id=? FOR UPDATE",fdId);
        if (state.isEmpty())throw error(HttpStatus.NOT_FOUND,"Fixed deposit not found");
        BankIslamicDepositGuard.requireConventional(jdbc, fdId, "approval submission");
        String status=(String)state.get(0).get("fd_sts");
        if (!((action.equals("PREMATURE_UPLIFT")&&"ACTIVE".equals(status)) ||
             (!action.equals("PREMATURE_UPLIFT")&&"MATURED".equals(status))))
            throw error(HttpStatus.CONFLICT,"FD is not eligible for this action");
        Integer pending=jdbc.queryForObject("""
          SELECT COUNT(*) FROM public.bank_fixed_deposit_approval_request
          WHERE fd_id=? AND request_status='PENDING'
          """,Integer.class,fdId);
        if(pending!=null && pending>0)throw error(HttpStatus.CONFLICT,"Another FD action is awaiting approval");
        // Posting validates all balances, eligibility, GL and settlement rules again on approval.
        Long id;
        try {
           id=jdbc.queryForObject("""
             INSERT INTO public.bank_fixed_deposit_approval_request
               (fd_id,action_type,payload,request_reason,request_status,requested_by)
             VALUES (?,?,CAST(? AS jsonb),?,'PENDING',?) RETURNING request_id
             """,Long.class,fdId,action,json(payload),reason.trim(),maker);
        } catch(DataIntegrityViolationException e) {
           throw error(HttpStatus.CONFLICT,"Duplicate pending FD request");
        }
        if (notifications != null) notifications.notifyRole("ADMIN", maker, "FIXED_DEPOSITS", "FD_APPROVAL",
                id, "PENDING", "Fixed Deposit approval required",
                "A " + action.replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + " request awaits review");
        return Map.of("requestId",id,"fdId",fdId,"actionType",action,"status","PENDING","requestedBy",maker);
    }
    @Transactional
    public Map<String, Object> assign(Long id, String checkerUsername, Authentication user) {
        String admin = actor(user);
        if (user.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw error(HttpStatus.FORBIDDEN, "Administrator role required");
        if (id == null || id <= 0 || checkerUsername == null || checkerUsername.isBlank()
                || checkerUsername.length() > 100)
            throw error(HttpStatus.BAD_REQUEST, "A checker must be selected");
        String checker = checkerUsername.trim();
        var requests = jdbc.queryForList("""
            SELECT request_id, requested_by, request_status, assigned_checker
            FROM public.bank_fixed_deposit_approval_request WHERE request_id = ? FOR UPDATE
            """, id);
        if (requests.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Approval request not found");
        var req = requests.get(0);
        if (!"PENDING".equals(req.get("request_status")))
            throw error(HttpStatus.CONFLICT, "Only pending requests may be assigned");
        if (checker.equalsIgnoreCase(String.valueOf(req.get("requested_by"))))
            throw error(HttpStatus.FORBIDDEN, "Maker cannot be assigned as checker");
        Integer eligible = jdbc.queryForObject("""
            SELECT COUNT(*) FROM public.app_user u
            JOIN public.app_user_role ur ON ur.user_id = u.user_id
            JOIN public.app_role role ON role.role_id = ur.role_id
            WHERE LOWER(u.username) = LOWER(?) AND u.enabled = TRUE AND role.role_nm = 'ADMIN'
            """, Integer.class, checker);
        if (eligible == null || eligible != 1)
            throw error(HttpStatus.CONFLICT, "Checker must be an enabled administrator");
        String previous = (String) req.get("assigned_checker");
        if (previous != null && previous.equalsIgnoreCase(checker))
            throw error(HttpStatus.CONFLICT, "Checker is already assigned");
        jdbc.update("""
            UPDATE public.bank_fixed_deposit_approval_request
            SET assigned_checker = ?, assigned_by = ?, assigned_at = CURRENT_TIMESTAMP
            WHERE request_id = ? AND request_status = 'PENDING'
            """, checker, admin, id);
        Long historyId = jdbc.queryForObject("""
            INSERT INTO public.bank_fd_approval_assignment_history
            (request_id, previous_checker, assigned_checker, assigned_by)
            VALUES (?, ?, ?, ?) RETURNING history_id
            """, Long.class, id, previous, checker, admin);
        if (notifications != null) {
            notifications.notifyUser(checker, "FIXED_DEPOSITS", "FD_APPROVAL", id,
                    "ASSIGNED_" + historyId, "Fixed Deposit approval assigned",
                    "A Fixed Deposit approval request has been assigned to you by " + admin);
            notifications.notifyUser(String.valueOf(req.get("requested_by")), "FIXED_DEPOSITS", "FD_APPROVAL", id,
                    "CHECKER_ASSIGNED_" + historyId, "Fixed Deposit checker assigned",
                    "Your Fixed Deposit request has been assigned to " + checker);
        }
        return Map.of("requestId", id, "assignedChecker", checker, "assignedBy", admin);
    }

    @Transactional
    public Map<String,Object> decide(Long id,boolean approve,String reason,Authentication user) {
        String checker=actor(user);
        if (id==null || id<=0)throw error(HttpStatus.BAD_REQUEST,"Request ID required");
        if(!approve)checkLength(reason,"Rejection reason",true);
        else checkLength(reason,"Decision reason",false);
        var requests=jdbc.queryForList("""
            SELECT request_id,fd_id,action_type,payload::text AS payload,
                   request_status,requested_by,assigned_checker FROM public.bank_fixed_deposit_approval_request
            WHERE request_id=? FOR UPDATE
            """,id);
        if(requests.isEmpty())throw error(HttpStatus.NOT_FOUND,"Approval request not found");
        var req=requests.get(0);
        if(!"PENDING".equals(req.get("request_status")))
            throw error(HttpStatus.CONFLICT,"Request already decided");
        if(checker.equalsIgnoreCase(String.valueOf(req.get("requested_by"))))
            throw error(HttpStatus.FORBIDDEN,"Requester cannot decide their own request");
        String assignedChecker = (String) req.get("assigned_checker");
        if (assignedChecker != null && !checker.equalsIgnoreCase(assignedChecker))
            throw error(HttpStatus.FORBIDDEN, "Approval request is assigned to another checker");
        Long fdId=((Number)req.get("fd_id")).longValue();
        String action=(String)req.get("action_type");
        Map<String,Object> posting=null;
        if(approve) {
            Map<String,Object> payload=parse((String)req.get("payload"));
            validatePayload(action,payload);
            posting=switch(action) {
               case "MATURITY_SETTLEMENT" -> maturity.settle(fdId,
                 new BankFixedDepositMaturitySettlementRequest(destination(payload),
                   payload.get("remarks") instanceof String r?r:null),checker);
               case "RENEWAL" -> renewal.renew(fdId,renewDto(payload),checker);
               case "PREMATURE_UPLIFT" -> uplift.withdraw(fdId,
                 new BankFixedDepositPrematureWithdrawalRequest(destination(payload),
                   payload.get("remarks") instanceof String r?r:null),user);
               default -> throw error(HttpStatus.CONFLICT,"Unsupported stored action");
            };
        }
        jdbc.update("""
           UPDATE public.bank_fixed_deposit_approval_request
           SET request_status=?, decided_by=?,decided_at=CURRENT_TIMESTAMP,
               decision_reason=?,posting_result=CAST(? AS jsonb)
           WHERE request_id=? AND request_status='PENDING'
           """,approve?"APPROVED":"REJECTED",checker,reason==null?null:reason.trim(),
               posting==null?null:json(posting),id);
        if (notifications != null) notifications.notifyUser(String.valueOf(req.get("requested_by")),
                "FIXED_DEPOSITS", "FD_APPROVAL", id, "DECIDED",
                "Fixed Deposit request " + (approve ? "approved" : "rejected"),
                "Your " + action.replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + " request was "
                        + (approve ? "approved" : "rejected") + " by " + checker);
        return Map.of("requestId",id,"fdId",fdId,"status",approve?"APPROVED":"REJECTED",
                      "decidedBy",checker,"actionType",action);
    }
}

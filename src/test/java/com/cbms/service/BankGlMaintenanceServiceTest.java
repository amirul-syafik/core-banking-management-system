package com.cbms.service;

import com.cbms.controller.BankGlMaintenanceController.ChangeRequest;
import com.cbms.controller.BankGlMaintenanceController.DecisionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BankGlMaintenanceServiceTest {
    static Authentication user(String name) { return new UsernamePasswordAuthenticationToken(name, null); }

    static class FakeJdbc extends JdbcTemplate {
        Map<String,Object> account;
        Map<String,Object> request;
        List<String> writes = new ArrayList<>();
        Object[] lastArgs;
        @Override public List<Map<String,Object>> queryForList(String sql, Object...args) {
            if (sql.contains("FROM public.bank_ledger_account"))
                return account == null ? List.of() : List.of(account);
            return List.of();
        }
        @Override public Map<String,Object> queryForMap(String sql,Object...args) {
            return request == null ? Map.of() : request;
        }
        @Override public <T>T queryForObject(String sql,Class<T> type,Object...args) {
            return type.cast(Long.valueOf(0));
        }
        @Override public int update(String sql,Object...args) {
            writes.add(sql);
            lastArgs=args;
            return 1;
        }
    }

    @Test void newCodeCreatesPendingRequestButDoesNotPostOrActivate() {
        FakeJdbc jdbc = new FakeJdbc();
        var service = new BankGlMaintenanceService(jdbc);
        var result = service.propose(new ChangeRequest("CREATE","210100","Islamic Deposit Liability",
                "LIABILITY","MYR","UAT chart of accounts setup"), user("maker"));
        assertEquals("SUBMITTED",result.get("status"));
        assertEquals(1,jdbc.writes.size());
        assertTrue(jdbc.writes.get(0).contains("INSERT INTO public.bank_gl_account_change_request"));
        assertFalse(jdbc.writes.get(0).contains("bank_ledger_entry"));
        assertFalse(jdbc.writes.get(0).contains("INSERT INTO public.bank_ledger_account"));
    }

    @Test void checkerCannotApproveOwnRequest() {
        FakeJdbc jdbc=new FakeJdbc();
        jdbc.request = Map.of("request_sts","SUBMITTED","requested_by","maker");
        var service=new BankGlMaintenanceService(jdbc);
        assertThrows(ResponseStatusException.class, () ->
                service.decide(1,new DecisionRequest(true,"Reviewed"),user("MAKER")));
        assertTrue(jdbc.writes.isEmpty());
    }

    @Test void fixedSystemGlCannotBeDeactivated() {
        FakeJdbc jdbc=new FakeJdbc();
        jdbc.account=Map.of("gl_cd","100100","gl_nm","Cash Control",
                "gl_typ","ASSET","ccy_cd","MYR","act_flg",true);
        var service=new BankGlMaintenanceService(jdbc);
        assertThrows(ResponseStatusException.class, () ->
                service.propose(new ChangeRequest("DEACTIVATE","100100","Cash Control",
                        "ASSET","MYR","UAT deactivation attempt"),user("maker")));
        assertTrue(jdbc.writes.isEmpty());
    }

    @Test void unchangedRenameDoesNotCreateRequest() {
        FakeJdbc jdbc=new FakeJdbc();
        jdbc.account=Map.of("gl_cd","220000","gl_nm","Example Liability",
                "gl_typ","LIABILITY","ccy_cd","MYR","act_flg",true);
        var service=new BankGlMaintenanceService(jdbc);
        assertThrows(ResponseStatusException.class, () ->
                service.propose(new ChangeRequest("RENAME","220000","Example Liability",
                        "LIABILITY","MYR","No actual edit"),user("maker")));
        assertTrue(jdbc.writes.isEmpty());
    }

    @Test void approvalOfCreateIsInitiallyInactive() {
        FakeJdbc jdbc=new FakeJdbc();
        jdbc.request=new HashMap<>();
        jdbc.request.put("request_sts","SUBMITTED");
        jdbc.request.put("requested_by","maker");
        jdbc.request.put("gl_cd","210100");
        jdbc.request.put("gl_nm","Islamic Deposit Liability");
        jdbc.request.put("gl_typ","LIABILITY");
        jdbc.request.put("ccy_cd","MYR");
        jdbc.request.put("action_typ","CREATE");
        jdbc.request.put("request_ref","GLM-2026-ABCDEFABCDEF");
        var service=new BankGlMaintenanceService(jdbc);
        var result=service.decide(1,new DecisionRequest(true,"Approved for testing"),user("checker"));
        assertEquals("APPROVED",result.get("status"));
        assertEquals(2,jdbc.writes.size());
        assertTrue(jdbc.writes.get(0).contains("bank_ledger_account"));
        assertTrue(jdbc.writes.get(0).contains("FALSE"));
        assertTrue(jdbc.writes.get(1).contains("bank_gl_account_change_request"));
    }

    @Test void rejectsInvalidRequestAndMissingRejectionReason() {
        var service=new BankGlMaintenanceService(new FakeJdbc());
        assertThrows(ResponseStatusException.class, () ->
                service.propose(new ChangeRequest("CREATE","xyz", "x", "ASSET","MYR","short"),user("maker")));
        assertThrows(ResponseStatusException.class, () ->
                service.decide(1,new DecisionRequest(false,""),user("checker")));
    }
}

package com.cbms;

import com.cbms.dto.BankFixedDepositMaturitySettlementRequest;
import com.cbms.service.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankFixedDepositApprovalServiceTest {
    JdbcTemplate jdbc;
    BankFixedDepositMaturitySettlementService maturity;
    BankFixedDepositRenewalService renewal;
    BankFixedDepositPrematureWithdrawalService uplift;
    BankFixedDepositApprovalService service;
    static Authentication user(String name) {
        return new UsernamePasswordAuthenticationToken(name,"N/A",List.of());
    }
    @BeforeEach void setup() {
        jdbc=mock(JdbcTemplate.class);
        maturity=mock(BankFixedDepositMaturitySettlementService.class);
        renewal=mock(BankFixedDepositRenewalService.class);
        uplift=mock(BankFixedDepositPrematureWithdrawalService.class);
        service=new BankFixedDepositApprovalService(jdbc,new ObjectMapper(),maturity,renewal,uplift);
    }
    Map<String,Object> request() {
        return Map.of("request_id",19L,"fd_id",3L,"action_type","MATURITY_SETTLEMENT",
          "payload","{\"destinationAccountId\":6}","request_status","PENDING","requested_by","maker");
    }
    @Test void invalidActionFailsBeforeDatabaseAccess() {
        var err=assertThrows(ResponseStatusException.class,()->service.submit(3L,"UNKNOWN",Map.of(),"reason",user("maker")));
        assertEquals(HttpStatus.BAD_REQUEST,err.getStatusCode());
        verifyNoInteractions(jdbc);
    }
    @Test void selfApprovalBlocked() {
        when(jdbc.queryForList(anyString(),eq(19L))).thenReturn(List.of(request()));
        var err=assertThrows(ResponseStatusException.class,()->service.decide(19L,true,null,user("maker")));
        assertEquals(HttpStatus.FORBIDDEN,err.getStatusCode());
        verifyNoInteractions(maturity,renewal,uplift);
    }
    @Test void approvalRunsPostingAsChecker() {
        when(jdbc.queryForList(anyString(),eq(19L))).thenReturn(List.of(request()));
        when(maturity.settle(eq(3L),any(BankFixedDepositMaturitySettlementRequest.class),eq("checker")))
          .thenReturn(Map.of("transactionId",35L));
        var result=service.decide(19L,true,"reviewed",user("checker"));
        assertEquals("APPROVED",result.get("status"));
        verify(maturity).settle(eq(3L),argThat(r->Long.valueOf(6L).equals(r.destinationAccountId())),eq("checker"));
    }
    @Test void failureDoesNotMarkRequestApproved() {
        when(jdbc.queryForList(anyString(),eq(19L))).thenReturn(List.of(request()));
        when(maturity.settle(eq(3L),any(BankFixedDepositMaturitySettlementRequest.class),eq("checker")))
          .thenThrow(new IllegalStateException("posting failure"));
        assertThrows(IllegalStateException.class,()->service.decide(19L,true,null,user("checker")));
        verify(jdbc,never()).update(contains("UPDATE public.bank_fixed_deposit_approval_request"),
            any(),any(),any(),any(),any());
    }
    @Test void rejectionMustHaveReason() {
        var err=assertThrows(ResponseStatusException.class,()->service.decide(19L,false,"",user("checker")));
        assertEquals(HttpStatus.BAD_REQUEST,err.getStatusCode());
        verifyNoInteractions(jdbc);
    }
}

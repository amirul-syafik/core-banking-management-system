package com.cbms.service;

import com.cbms.dto.BankAccountApplicationDetailsResponse;
import com.cbms.dto.BankAccountApplicationHistoryResponse;
import com.cbms.entity.BankAccountApplication;
import com.cbms.entity.BankAccountApplicationHistory;
import com.cbms.repository.BankAccountApplicationHistoryRepository;
import com.cbms.repository.BankAccountApplicationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class BankAccountApplicationDetailsService {

    private final BankAccountApplicationRepository applicationRepository;
    private final BankAccountApplicationHistoryRepository historyRepository;
    private final JdbcTemplate jdbcTemplate;

    public BankAccountApplicationDetailsService(
            BankAccountApplicationRepository applicationRepository,
            BankAccountApplicationHistoryRepository historyRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public BankAccountApplicationDetailsResponse getDetails(Long applId) {

        BankAccountApplication application =
                applicationRepository.findById(applId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Application not found"
                        ));

        Map<String, Object> customer = firstRow(
                """
                        SELECT cust_nm, sts
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                application.getCustId()
        );

        // Latest KYC assessment, not any historical verified record.
        Map<String, Object> kyc = firstRow(
                """
                        SELECT asmt_sts, risk_rtng
                        FROM public.customer_kyc
                        WHERE cust_id = ?
                        ORDER BY upd_dt DESC NULLS LAST, kyc_id DESC
                        LIMIT 1
                        """,
                application.getCustId()
        );

        Map<String, Object> product = firstRow(
                """
                        SELECT prod_cd, prod_nm, prod_cat
                        FROM public.bank_product
                        WHERE prod_id = ?
                        """,
                application.getProdId()
        );

        Map<String, Object> scheme = firstRow(
                """
                        SELECT schm_cd, schm_nm
                        FROM public.bank_scheme
                        WHERE schm_id = ?
                        """,
                application.getSchmId()
        );

        Map<String, Object> account =
                application.getAcctId() == null
                        ? Map.of()
                        : firstRow(
                        """
                                SELECT acct_no, acct_typ, acct_sts
                                FROM public.bank_account
                                WHERE acct_id = ?
                                """,
                        application.getAcctId()
                );

        List<BankAccountApplicationHistoryResponse> history =
                historyRepository
                        .findByApplIdOrderByActionDtAscHistIdAsc(applId)
                        .stream()
                        .map(this::toHistoryResponse)
                        .toList();

        return new BankAccountApplicationDetailsResponse(

                application.getApplId(),
                application.getApplRef(),
                application.getApplSts(),

                application.getCustId(),
                str(customer, "cust_nm"),
                str(customer, "sts"),

                str(kyc, "asmt_sts"),
                str(kyc, "risk_rtng"),

                application.getProdId(),
                str(product, "prod_cd"),
                str(product, "prod_nm"),
                str(product, "prod_cat"),

                application.getSchmId(),
                str(scheme, "schm_cd"),
                str(scheme, "schm_nm"),

                application.getReqAmt(),
                application.getReqTenor(),
                application.getRemarks(),

                application.getAcctId(),
                str(account, "acct_no"),
                str(account, "acct_typ"),
                str(account, "acct_sts"),

                application.getCrtBy(),
                application.getCrtDt(),

                application.getSubmittedBy(),
                application.getSubmittedDt(),

                application.getReviewedBy(),
                application.getReviewedDt(),
                application.getReviewRemarks(),

                history
        );
    }

    private Map<String, Object> firstRow(
            String sql,
            Object... params
    ) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(sql, params);

        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private BankAccountApplicationHistoryResponse toHistoryResponse(
            BankAccountApplicationHistory history
    ) {
        return new BankAccountApplicationHistoryResponse(
                history.getHistId(),
                history.getOldSts(),
                history.getNewSts(),
                history.getActionTyp(),
                history.getRemarks(),
                history.getActionBy(),
                history.getActionDt()
        );
    }
}

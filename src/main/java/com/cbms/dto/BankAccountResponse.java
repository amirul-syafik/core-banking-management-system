package com.cbms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BankAccountResponse(
        Long acctId,
        Long custId,
        String acctNo,
        String acctTyp,
        String ccyCd,
        String acctSts,
        LocalDate openDt,
        LocalDate closeDt,
        String crtBy,
        OffsetDateTime crtDt
) {
}
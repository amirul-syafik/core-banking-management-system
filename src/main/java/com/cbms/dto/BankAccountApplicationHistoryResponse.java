package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankAccountApplicationHistoryResponse(
        Long histId,
        String oldSts,
        String newSts,
        String actionTyp,
        String remarks,
        String actionBy,
        OffsetDateTime actionDt
) {}
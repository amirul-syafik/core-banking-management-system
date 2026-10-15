package com.cbms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CustomerIdentificationResponse(
        Long idnId,
        Long custId,
        String docTyp,
        String maskedDocNo,
        String issCty,
        LocalDate issDt,
        LocalDate expDt,
        Boolean priFlg,
        Long docId,
        String crtBy,
        OffsetDateTime crtDt
) {
}

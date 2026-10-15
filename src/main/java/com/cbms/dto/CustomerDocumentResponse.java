package com.cbms.dto;

import java.time.OffsetDateTime;

public record CustomerDocumentResponse(
        Long docId,
        Long custId,
        String fileNm,
        String fileTyp,
        Long fileSize,
        OffsetDateTime uploadedDt
) {
}
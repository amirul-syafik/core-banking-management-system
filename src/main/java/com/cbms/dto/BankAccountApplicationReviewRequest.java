package com.cbms.dto;

import jakarta.validation.constraints.Size;

public record BankAccountApplicationReviewRequest(
        @Size(max = 1000)
        String remarks
) {
}
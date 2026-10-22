package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankProductUpdateRequest(
        @NotBlank
        @Size(max = 150)
        String prodNm,

        @Size(max = 500)
        String prodDesc
) {
}
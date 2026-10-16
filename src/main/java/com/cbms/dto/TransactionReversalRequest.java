package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransactionReversalRequest(

        @NotBlank
        @Size(max = 250)
        String reason

) {
}
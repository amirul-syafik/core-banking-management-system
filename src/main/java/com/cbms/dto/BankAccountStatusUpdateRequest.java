package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BankAccountStatusUpdateRequest(

        @NotBlank
        @Pattern(regexp = "ACTIVE|DORMANT|FROZEN|CLOSED")
        String status

) {
}
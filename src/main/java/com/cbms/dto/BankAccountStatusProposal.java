package com.cbms.dto;

import jakarta.validation.constraints.*;

public record BankAccountStatusProposal(@NotBlank @Pattern(regexp = "ACTIVE|DORMANT|FROZEN") String status,
                                        @NotBlank @Size(max = 500) String reason) {
}

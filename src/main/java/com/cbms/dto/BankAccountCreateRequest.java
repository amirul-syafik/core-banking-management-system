package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BankAccountCreateRequest(

        @NotBlank
        @Pattern(regexp = "SAVINGS|CURRENT")
        String acctTyp,

        @NotBlank
        @Pattern(regexp = "MYR")
        String ccyCd
) {
}
package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BankProductCreateRequest(

        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Z0-9_-]+$")
        String prodCd,

        @NotBlank
        @Size(max = 150)
        String prodNm,

        @NotBlank
        @Pattern(regexp =
                "SAVINGS|CURRENT|FIXED_DEPOSIT|HIRE_PURCHASE|" +
                        "WHOLESALE_FINANCING|FLOOR_STOCKING|CREDIT_LINE")
        String prodCat,

        @NotBlank
        @Pattern(regexp = "CONVENTIONAL|ISLAMIC")
        String bankingTyp,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String ccyCd,

        @Size(max = 500)
        String prodDesc

) {}

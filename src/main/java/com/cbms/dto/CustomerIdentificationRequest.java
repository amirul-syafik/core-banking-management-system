package com.cbms.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CustomerIdentificationRequest(

        @NotBlank
        @Size(max = 30)
        String docTyp,

        @NotBlank
        @Size(max = 100)
        String docNo,

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{2}")
        String issCty,

        LocalDate issDt,

        LocalDate expDt,

        Boolean priFlg,

        Long docId
) {
}

package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerCreateRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 200, message = "Customer name must not exceed 200 characters")
    private String custNm;

    @NotBlank(message = "Customer type is required")
    @Pattern(
            regexp = "INDIVIDUAL|CORPORATE",
            message = "Customer type must be INDIVIDUAL or CORPORATE"
    )
    private String custTyp;

    public String getCustNm() {
        return custNm;
    }

    public void setCustNm(String custNm) {
        this.custNm = custNm;
    }

    public String getCustTyp() {
        return custTyp;
    }

    public void setCustTyp(String custTyp) {
        this.custTyp = custTyp;
    }
}

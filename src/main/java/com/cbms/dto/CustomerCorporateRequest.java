package com.cbms.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CustomerCorporateRequest {

    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    private String regNo;

    @Pattern(
            regexp = "[A-Za-z]{2}",
            message = "Registration country must contain two letters"
    )
    private String regCty;

    private LocalDate incDt;

    @Size(max = 100, message = "Business type must not exceed 100 characters")
    private String bizTyp;

    public String getRegNo() {
        return regNo;
    }

    public void setRegNo(String regNo) {
        this.regNo = regNo;
    }

    public String getRegCty() {
        return regCty;
    }

    public void setRegCty(String regCty) {
        this.regCty = regCty;
    }

    public LocalDate getIncDt() {
        return incDt;
    }

    public void setIncDt(LocalDate incDt) {
        this.incDt = incDt;
    }

    public String getBizTyp() {
        return bizTyp;
    }

    public void setBizTyp(String bizTyp) {
        this.bizTyp = bizTyp;
    }
}

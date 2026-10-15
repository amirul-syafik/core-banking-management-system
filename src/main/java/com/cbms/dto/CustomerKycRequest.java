package com.cbms.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CustomerKycRequest {

    @NotBlank
    @Pattern(
            regexp = "PENDING|IN_REVIEW|APPROVED|REJECTED|EXPIRED",
            message = "Invalid KYC assessment status"
    )
    private String asmtSts;

    @NotBlank
    @Pattern(
            regexp = "LOW|MEDIUM|HIGH",
            message = "Risk rating must be LOW, MEDIUM or HIGH"
    )
    private String riskRtng;

    @FutureOrPresent
    private LocalDate revDt;

    @FutureOrPresent
    private LocalDate nxtRevDt;

    @Size(max = 1000)
    private String rmk;

    public String getAsmtSts() {
        return asmtSts;
    }

    public void setAsmtSts(String asmtSts) {
        this.asmtSts = asmtSts;
    }

    public String getRiskRtng() {
        return riskRtng;
    }

    public void setRiskRtng(String riskRtng) {
        this.riskRtng = riskRtng;
    }

    public LocalDate getRevDt() {
        return revDt;
    }

    public void setRevDt(LocalDate revDt) {
        this.revDt = revDt;
    }

    public LocalDate getNxtRevDt() {
        return nxtRevDt;
    }

    public void setNxtRevDt(LocalDate nxtRevDt) {
        this.nxtRevDt = nxtRevDt;
    }

    public String getRmk() {
        return rmk;
    }

    public void setRmk(String rmk) {
        this.rmk = rmk;
    }
}
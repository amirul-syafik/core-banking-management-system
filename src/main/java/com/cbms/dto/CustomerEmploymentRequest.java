package com.cbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CustomerEmploymentRequest {

    @NotBlank(message = "Employment type is required")
    @Pattern(
            regexp = "FULL_TIME|PART_TIME|SELF_EMPLOYED|CONTRACT|UNEMPLOYED|RETIRED|OTHER",
            message = "Invalid employment type"
    )
    private String empTyp;

    @NotBlank(message = "Employment status is required")
    @Pattern(
            regexp = "CURRENT|PREVIOUS|PENDING",
            message = "Employment status must be CURRENT, PREVIOUS, or PENDING"
    )
    private String empSts;

    @Size(max = 200, message = "Employer name must not exceed 200 characters")
    private String empNm;

    @Size(max = 100, message = "Occupation must not exceed 100 characters")
    private String occ;

    @Size(max = 20, message = "Industry code must not exceed 20 characters")
    private String indCd;

    private LocalDate startDt;

    private LocalDate endDt;

    @DecimalMin(value = "0.00", message = "Monthly income cannot be negative")
    @Digits(integer = 16, fraction = 2, message = "Monthly income must have up to 16 integer digits and 2 decimal places")
    private BigDecimal mthInc;

    @Pattern(
            regexp = "[A-Za-z]{3}",
            message = "Currency must be a three-letter code, such as MYR"
    )
    private String incCcy;

    public String getEmpTyp() {
        return empTyp;
    }

    public void setEmpTyp(String empTyp) {
        this.empTyp = empTyp;
    }

    public String getEmpSts() {
        return empSts;
    }

    public void setEmpSts(String empSts) {
        this.empSts = empSts;
    }

    public String getEmpNm() {
        return empNm;
    }

    public void setEmpNm(String empNm) {
        this.empNm = empNm;
    }

    public String getOcc() {
        return occ;
    }

    public void setOcc(String occ) {
        this.occ = occ;
    }

    public String getIndCd() {
        return indCd;
    }

    public void setIndCd(String indCd) {
        this.indCd = indCd;
    }

    public LocalDate getStartDt() {
        return startDt;
    }

    public void setStartDt(LocalDate startDt) {
        this.startDt = startDt;
    }

    public LocalDate getEndDt() {
        return endDt;
    }

    public void setEndDt(LocalDate endDt) {
        this.endDt = endDt;
    }

    public BigDecimal getMthInc() {
        return mthInc;
    }

    public void setMthInc(BigDecimal mthInc) {
        this.mthInc = mthInc;
    }

    public String getIncCcy() {
        return incCcy;
    }

    public void setIncCcy(String incCcy) {
        this.incCcy = incCcy;
    }
}

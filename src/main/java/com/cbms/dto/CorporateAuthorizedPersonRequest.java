package com.cbms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CorporateAuthorizedPersonRequest {

    @NotNull(message = "Authorized customer ID is required")
    private Long authCustId;

    @NotNull(message = "Authorization type is required")
    @Pattern(
            regexp = "SIGNATORY|DIRECTOR|REPRESENTATIVE|ACCOUNT_OPERATOR|OTHER",
            message = "Invalid authorization type"
    )
    private String authTyp;

    @Size(max = 100, message = "Authorization reference must not exceed 100 characters")
    private String authRef;

    private LocalDate effDt;

    private LocalDate endDt;

    @Pattern(
            regexp = "ACTIVE|INACTIVE|REVOKED",
            message = "Status must be ACTIVE, INACTIVE, or REVOKED"
    )
    private String sts;

    @Size(max = 1000, message = "Remark must not exceed 1000 characters")
    private String rmk;

    public Long getAuthCustId() {
        return authCustId;
    }

    public void setAuthCustId(Long authCustId) {
        this.authCustId = authCustId;
    }

    public String getAuthTyp() {
        return authTyp;
    }

    public void setAuthTyp(String authTyp) {
        this.authTyp = authTyp;
    }

    public String getAuthRef() {
        return authRef;
    }

    public void setAuthRef(String authRef) {
        this.authRef = authRef;
    }

    public LocalDate getEffDt() {
        return effDt;
    }

    public void setEffDt(LocalDate effDt) {
        this.effDt = effDt;
    }

    public LocalDate getEndDt() {
        return endDt;
    }

    public void setEndDt(LocalDate endDt) {
        this.endDt = endDt;
    }

    public String getSts() {
        return sts;
    }

    public void setSts(String sts) {
        this.sts = sts;
    }

    public String getRmk() {
        return rmk;
    }

    public void setRmk(String rmk) {
        this.rmk = rmk;
    }
}

package com.cbms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public class CustomerRelationshipRequest {

    @NotNull
    private Long relCustId;

    @Pattern(
            regexp = "SPOUSE|JOINT_APPLICANT|GUARANTOR|DIRECTOR|SHAREHOLDER|PARENT|SUBSIDIARY|PARENT_GUARDIAN|OTHER",
            message = "Invalid relationship type"
    )
    private String relTyp;

    @Pattern(
            regexp = "ACTIVE|INACTIVE|PENDING",
            message = "Status must be ACTIVE, INACTIVE or PENDING"
    )
    private String sts;

    private LocalDate effDt;

    private LocalDate endDt;

    public Long getRelCustId() {
        return relCustId;
    }

    public void setRelCustId(Long relCustId) {
        this.relCustId = relCustId;
    }

    public String getRelTyp() {
        return relTyp;
    }

    public void setRelTyp(String relTyp) {
        this.relTyp = relTyp;
    }

    public String getSts() {
        return sts;
    }

    public void setSts(String sts) {
        this.sts = sts;
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
}
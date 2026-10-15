package com.cbms.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerContactRequest {

    @Pattern(
            regexp = "MOBILE|PHONE|EMAIL|WORK_PHONE|OTHER",
            message = "Invalid contact type"
    )
    private String conTyp;

    @Size(max = 200)
    private String conVal;

    private Boolean priFlg;

    public String getConTyp() {
        return conTyp;
    }

    public void setConTyp(String conTyp) {
        this.conTyp = conTyp;
    }

    public String getConVal() {
        return conVal;
    }

    public void setConVal(String conVal) {
        this.conVal = conVal;
    }

    public Boolean getPriFlg() {
        return priFlg;
    }

    public void setPriFlg(Boolean priFlg) {
        this.priFlg = priFlg;
    }
}
package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CustomerIndividualRequest {

    private LocalDate dob;

    @Pattern(
            regexp = "MALE|FEMALE|OTHER|NOT_SPECIFIED",
            message = "Invalid gender"
    )
    private String gndr;

    @Pattern(
            regexp = "[A-Za-z]{2}",
            message = "Nationality code must contain two letters"
    )
    private String natCd;

    @Pattern(
            regexp = "SINGLE|MARRIED|DIVORCED|WIDOWED|OTHER",
            message = "Invalid marital status"
    )
    private String mrtlSts;

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getGndr() {
        return gndr;
    }

    public void setGndr(String gndr) {
        this.gndr = gndr;
    }

    public String getNatCd() {
        return natCd;
    }

    public void setNatCd(String natCd) {
        this.natCd = natCd;
    }

    public String getMrtlSts() {
        return mrtlSts;
    }

    public void setMrtlSts(String mrtlSts) {
        this.mrtlSts = mrtlSts;
    }
}

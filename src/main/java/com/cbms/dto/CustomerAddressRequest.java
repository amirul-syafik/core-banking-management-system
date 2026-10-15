package com.cbms.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerAddressRequest {

    @Pattern(
            regexp = "RESIDENTIAL|BUSINESS|MAILING|REGISTERED|OTHER",
            message = "Address type must be RESIDENTIAL, BUSINESS, MAILING, REGISTERED or OTHER"
    )
    private String adrTyp;

    @Size(max = 200)
    private String adrLn1;

    @Size(max = 200)
    private String adrLn2;

    @Size(max = 200)
    private String adrLn3;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String stProv;

    @Size(max = 20)
    private String pstCd;

    @Pattern(
            regexp = "[A-Za-z]{2}",
            message = "Country code must contain two letters"
    )
    private String ctyCd;

    private Boolean priFlg;

    public String getAdrTyp() {
        return adrTyp;
    }

    public void setAdrTyp(String adrTyp) {
        this.adrTyp = adrTyp;
    }

    public String getAdrLn1() {
        return adrLn1;
    }

    public void setAdrLn1(String adrLn1) {
        this.adrLn1 = adrLn1;
    }

    public String getAdrLn2() {
        return adrLn2;
    }

    public void setAdrLn2(String adrLn2) {
        this.adrLn2 = adrLn2;
    }

    public String getAdrLn3() {
        return adrLn3;
    }

    public void setAdrLn3(String adrLn3) {
        this.adrLn3 = adrLn3;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStProv() {
        return stProv;
    }

    public void setStProv(String stProv) {
        this.stProv = stProv;
    }

    public String getPstCd() {
        return pstCd;
    }

    public void setPstCd(String pstCd) {
        this.pstCd = pstCd;
    }

    public String getCtyCd() {
        return ctyCd;
    }

    public void setCtyCd(String ctyCd) {
        this.ctyCd = ctyCd;
    }

    public Boolean getPriFlg() {
        return priFlg;
    }

    public void setPriFlg(Boolean priFlg) {
        this.priFlg = priFlg;
    }
}
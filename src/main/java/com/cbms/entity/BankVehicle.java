package com.cbms.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_vehicle", schema = "public")
public class BankVehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_id")
    private Long vehicleId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_cust_id", nullable = false)
    private Customer owner;
    @Column(name = "registration_no", length = 30)
    private String registrationNo;
    @Column(name = "registration_dt")
    private LocalDate registrationDt;
    @Column(name = "chassis_no", nullable = false, length = 100)
    private String chassisNo;
    @Column(name = "engine_no", length = 100)
    private String engineNo;
    @Column(name = "vehicle_make", nullable = false, length = 100)
    private String vehicleMake;
    @Column(name = "vehicle_model", nullable = false, length = 100)
    private String vehicleModel;
    @Column(name = "vehicle_variant", length = 100)
    private String vehicleVariant;
    @Column(name = "manufacture_year", nullable = false)
    private Integer manufactureYear;
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private String vehicleType;
    @Column(name = "vehicle_condition", nullable = false, length = 20)
    private String vehicleCondition;
    @Column(name = "colour", length = 50)
    private String colour;
    @Column(name = "engine_capacity_cc")
    private Integer engineCapacityCc;
    @Column(name = "vehicle_sts", nullable = false, length = 20)
    private String vehicleSts;
    @Column(name = "crt_dt", nullable = false, updatable = false)
    private OffsetDateTime crtDt;
    @Column(name = "crt_by", nullable = false, updatable = false, length = 100)
    private String crtBy;
    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;
    @Column(name = "upd_by", length = 100)
    private String updBy;

    public Long getVehicleId() {
        return vehicleId;
    }

    public Customer getOwner() {
        return owner;
    }

    public void setOwner(Customer v) {
        owner = v;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(String v) {
        registrationNo = v;
    }

    public LocalDate getRegistrationDt() {
        return registrationDt;
    }

    public void setRegistrationDt(LocalDate v) {
        registrationDt = v;
    }

    public String getChassisNo() {
        return chassisNo;
    }

    public void setChassisNo(String v) {
        chassisNo = v;
    }

    public String getEngineNo() {
        return engineNo;
    }

    public void setEngineNo(String v) {
        engineNo = v;
    }

    public String getVehicleMake() {
        return vehicleMake;
    }

    public void setVehicleMake(String v) {
        vehicleMake = v;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String v) {
        vehicleModel = v;
    }

    public String getVehicleVariant() {
        return vehicleVariant;
    }

    public void setVehicleVariant(String v) {
        vehicleVariant = v;
    }

    public Integer getManufactureYear() {
        return manufactureYear;
    }

    public void setManufactureYear(Integer v) {
        manufactureYear = v;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String v) {
        vehicleType = v;
    }

    public String getVehicleCondition() {
        return vehicleCondition;
    }

    public void setVehicleCondition(String v) {
        vehicleCondition = v;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String v) {
        colour = v;
    }

    public Integer getEngineCapacityCc() {
        return engineCapacityCc;
    }

    public void setEngineCapacityCc(Integer v) {
        engineCapacityCc = v;
    }

    public String getVehicleSts() {
        return vehicleSts;
    }

    public void setVehicleSts(String v) {
        vehicleSts = v;
    }

    public OffsetDateTime getCrtDt() {
        return crtDt;
    }

    public void setCrtDt(OffsetDateTime v) {
        crtDt = v;
    }

    public String getCrtBy() {
        return crtBy;
    }

    public void setCrtBy(String v) {
        crtBy = v;
    }

    public OffsetDateTime getUpdDt() {
        return updDt;
    }

    public void setUpdDt(OffsetDateTime v) {
        updDt = v;
    }

    public String getUpdBy() {
        return updBy;
    }

    public void setUpdBy(String v) {
        updBy = v;
    }
}

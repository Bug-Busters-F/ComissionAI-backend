package com.bugbusters.backend.importbase.dto;

import java.time.LocalDate;

public class HrFileRow {
    private LocalDate referenceDate;
    private Integer brandCode;
    private String brandDescription;
    private Integer storeCode;
    private String storeDescription;
    private String registration;
    private LocalDate admissDate;
    private LocalDate demissDate;
    private Integer positionCode;
    private String positionDescription;

    public HrFileRow(LocalDate referenceDate, Integer brandCode, String brandDescription, Integer storeCode,
            String storeDescription, String registration, LocalDate admissDate, LocalDate demissDate,
            Integer positionCode, String positionDescription) {
        this.referenceDate = referenceDate;
        this.brandCode = brandCode;
        this.brandDescription = brandDescription;
        this.storeCode = storeCode;
        this.storeDescription = storeDescription;
        this.registration = registration;
        this.admissDate = admissDate;
        this.demissDate = demissDate;
        this.positionCode = positionCode;
        this.positionDescription = positionDescription;
    }

    public LocalDate getReferenceDate() {
        return referenceDate;
    }

    public Integer getBrandCode() {
        return brandCode;
    }

    public String getBrandDescription() {
        return brandDescription;
    }

    public Integer getStoreCode() {
        return storeCode;
    }

    public String getStoreDescription() {
        return storeDescription;
    }

    public LocalDate getAdmissDate() {
        return admissDate;
    }

    public LocalDate getDemissDate() {
        return demissDate;
    }

    public Integer getPositionCode() {
        return positionCode;
    }

    public String getPositionDescription() {
        return positionDescription;
    }

    public String getRegistration() {
        return registration;
    }

}

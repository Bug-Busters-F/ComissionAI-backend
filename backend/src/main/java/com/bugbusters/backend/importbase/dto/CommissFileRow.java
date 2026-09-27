package com.bugbusters.backend.importbase.dto;

import java.math.BigDecimal;

public class CommissFileRow {
    private Integer brandCode;
    private String brandDescription;
    private Integer positionCode;
    private String positionDescription;
    private BigDecimal percentage;

    public CommissFileRow(Integer brandCode, String brandDescription, Integer positionCode, String positionDescription,
            BigDecimal percentage) {
        this.brandCode = brandCode;
        this.brandDescription = brandDescription;
        this.positionCode = positionCode;
        this.positionDescription = positionDescription;
        this.percentage = percentage;
    }

    public Integer getBrandCode() {
        return brandCode;
    }

    public String getBrandDescription() {
        return brandDescription;
    }

    public Integer getPositionCode() {
        return positionCode;
    }

    public String getPositionDescription() {
        return positionDescription;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

}

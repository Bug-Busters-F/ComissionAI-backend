package com.bugbusters.backend.importbase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesFileRow {
    private LocalDate referenceDate;
    private Integer brandCode;
    private String brandDescription;
    private Integer storeCode;
    private String storeDescription;
    private String registration;
    private BigDecimal saleValue;
    private int linha;

    public SalesFileRow(LocalDate referenceDate, Integer brandCode, String brandDescription, Integer storeCode,
        String storeDescription, String registration, BigDecimal saleValue) {
        this(referenceDate, brandCode, brandDescription, storeCode, storeDescription, registration, saleValue, 0);
    }

    public SalesFileRow(LocalDate referenceDate, Integer brandCode, String brandDescription, Integer storeCode,
        String storeDescription, String registration, BigDecimal saleValue, int linha) {
        this.referenceDate = referenceDate;
        this.brandCode = brandCode;
        this.brandDescription = brandDescription;
        this.storeCode = storeCode;
        this.storeDescription = storeDescription;
        this.registration = registration;
        this.saleValue = saleValue;
        this.linha = linha;
    }

    public int getLinha() {
        return linha;
    }

    public void setLinha(int linha) {
        this.linha = linha;
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


    public String getRegistration() {
        return registration;
    }


    public BigDecimal getSaleValue() {
        return saleValue;
    }
}

package com.bugbusters.backend.basecomiss;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.position.Position;

@Entity
@Table(name = "tb_basecomiss", uniqueConstraints = {
    @UniqueConstraint(name = "uk_basecomiss_brand_position_month", columnNames = {"brand_id", "position_id", "reference_month"})
})
public class BaseComiss {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @ManyToOne
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal percentage;

    @Column(nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "id_lote_origem", length = 100)
    private String idLoteOrigem;

    protected BaseComiss() {
    }

    @Deprecated
    public BaseComiss(
            Brand brand,
            Position position,
            BigDecimal percentage
    ) {
        this(brand, position, percentage, LocalDate.now().withDayOfMonth(1));
    }

    public BaseComiss(
            Brand brand,
            Position position,
            BigDecimal percentage,
            LocalDate referenceMonth
    ) {
        this.brand = brand;
        this.position = position;
        this.percentage = percentage;
        this.referenceMonth = referenceMonth != null ? referenceMonth.withDayOfMonth(1) : null;
    }

    public UUID getId() {
        return id;
    }

    public Brand getBrand() {
        return brand;
    }

    public Position getPosition() {
        return position;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public LocalDate getReferenceMonth() {
        return referenceMonth;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public void setReferenceMonth(LocalDate referenceMonth) {
        this.referenceMonth = referenceMonth;
    }

    public String getIdLoteOrigem() {
        return idLoteOrigem;
    }

    public void setIdLoteOrigem(String idLoteOrigem) {
        this.idLoteOrigem = idLoteOrigem;
    }
}

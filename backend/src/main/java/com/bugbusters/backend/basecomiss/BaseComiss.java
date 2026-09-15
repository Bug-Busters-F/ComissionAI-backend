package com.bugbusters.backend.basecomiss;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.position.Position;

@Entity 
@Table (name = "tb_basecomiss")
public class BaseComiss {
    @Id
    @GeneratedValue (strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn (name = "brand_id", nullable = false)
    private Brand brand;

    @ManyToOne
    @JoinColumn (name = "position_id", nullable = false)
    private Position position;

    @Column(nullable = false)
    private Double percentage;

    @Column(nullable = false)
    private LocalDate referenceMonth;

    public UUID getId() {
        return id;
    }

    public Brand getBrand() {
        return brand;
    }

    public Position getPosition() {
        return position;
    }

    public Double getPercentage() {
        return percentage;
    }

    public LocalDate getReferenceMonth() {
        return referenceMonth;
    }
}

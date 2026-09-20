package com.bugbusters.backend.sales;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.store.Store;

@Entity
@Table(
        name = "tb-salses",
        uniqueConstraints = @UniqueConstraint(name = "uk_venda_id_externo", columnNames = "id_venda_externo")
)
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne
    @JoinColumn (name = "registration_id", nullable = false)
    private Registration registration;

    @ManyToOne
    @JoinColumn (name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn (name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false, length = 150)
    private LocalDate saleDate;

    @Column(nullable = true, length = 150)
    private String referencemonth;

    @Column(name = "data_venda", nullable = false)
    private String saleChannel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;


    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Registration getRegistration() {
        return registration;
    }


    public void setRegistration(Registration registration) {
        this.registration = registration;
    }


    public Store getStore() {
        return store;
    }


    public void setStore(Store store) {
        this.store = store;
    }


    public Brand getBrand() {
        return brand;
    }


    public void setBrand(Brand brand) {
        this.brand = brand;
    }


    public Double getValue() {
        return value;
    }


    public void setValue(Double value) {
        this.value = value;
    }


    public LocalDate getSaleDate() {
        return saleDate;
    }


    public void setSaleDate(LocalDate saleDate) {
        this.saleDate = saleDate;
    }


    public String getReferencemonth() {
        return referencemonth;
    }


    public void setReferencemonth(String referencemonth) {
        this.referencemonth = referencemonth;
    }


    public String getSaleChannel() {
        return saleChannel;
    }


    public void setSaleChannel(String saleChannel) {
        this.saleChannel = saleChannel;
    }


    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }



    public UUID getId() {
        return id;
    }
   
}

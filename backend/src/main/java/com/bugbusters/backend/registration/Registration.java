package com.bugbusters.backend.registration;

import java.util.Date;
import java.util.UUID;

import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.store.Store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity 
@Table (name = "tb_registration")
public class Registration {
    @Id 
    @GeneratedValue (strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn (name = "store_id", nullable = false)
    private Store store;

    @ManyToOne 
    @JoinColumn (name = "position_id", nullable = false)
    private Position position;

    @Column (nullable = false, unique = true)
    private String registration;

    @Column (nullable = false)
    private Date admissDate;

    @Column (nullable = true)
    private Date demissDate;

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public String getRegistration() {
        return registration;
    }

    public void setRegistration(String registration) {
        this.registration = registration;
    }

    public Date getAdmissDate() {
        return admissDate;
    }

    public void setAdmissDate(Date admissDate) {
        this.admissDate = admissDate;
    }

    public Date getDemissDate() {
        return demissDate;
    }

    public void setDemissDate(Date demissDate) {
        this.demissDate = demissDate;
    }
}

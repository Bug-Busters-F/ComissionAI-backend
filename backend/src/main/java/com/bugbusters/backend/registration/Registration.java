package com.bugbusters.backend.registration;

import java.time.LocalDate;
import java.util.UUID;

import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.store.Store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
    private LocalDate admissDate;

    @Column (nullable = true)
    private LocalDate demissDate;

    @Column(name = "id_lote_origem", length = 100)
    private String idLoteOrigem;

    @Column(nullable = false)
    private int versao = 1;

    @Column(name = "origem_ultima_alteracao", nullable = false, length = 30)
    private String origemUltimaAlteracao = "IMPORTACAO";

    @Column(name = "id_lote_ultima_alteracao", length = 100)
    private String idLoteUltimaAlteracao;

    @Column(name = "atualizado_em")
    private java.time.OffsetDateTime atualizadoEm;

    @Column(name = "atualizado_por", length = 150)
    private String atualizadoPor;

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

    public LocalDate getAdmissDate() {
        return admissDate;
    }

    public void setAdmissDate(LocalDate admissDate) {
        this.admissDate = admissDate;
    }

    public LocalDate getDemissDate() {
        return demissDate;
    }

    public void setDemissDate(LocalDate demissDate) {
        this.demissDate = demissDate;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIdLoteOrigem() {
        return idLoteOrigem;
    }

    public void setIdLoteOrigem(String idLoteOrigem) {
        this.idLoteOrigem = idLoteOrigem;
    }

    public int getVersao() {
        return versao;
    }

    public void setVersao(int versao) {
        this.versao = versao;
    }

    public String getOrigemUltimaAlteracao() {
        return origemUltimaAlteracao;
    }

    public void setOrigemUltimaAlteracao(String origemUltimaAlteracao) {
        this.origemUltimaAlteracao = origemUltimaAlteracao;
    }

    public String getIdLoteUltimaAlteracao() {
        return idLoteUltimaAlteracao;
    }

    public void setIdLoteUltimaAlteracao(String idLoteUltimaAlteracao) {
        this.idLoteUltimaAlteracao = idLoteUltimaAlteracao;
    }

    public java.time.OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(java.time.OffsetDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public void setAtualizadoPor(String atualizadoPor) {
        this.atualizadoPor = atualizadoPor;
    }
}

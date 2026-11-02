package com.bugbusters.backend.sales;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.store.Store;

@Entity
@Table(name = "tb_sales")
public class Sale {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "valor_venda", nullable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private LocalDate saleDate;

    @Column(name = "canal", nullable = true)
    private String saleChannel;

    @Column(name = "tipo_venda", length = 50)
    private String tipoVenda = "INFORMADA";

    @Column(name = "id_lote_origem", length = 100)
    private String idLoteOrigem;

    @Column(nullable = false)
    private int versao = 1;

    @Column(name = "origem_ultima_alteracao", nullable = false, length = 30)
    private String origemUltimaAlteracao = "IMPORTACAO";

    @Column(name = "id_lote_ultima_alteracao", length = 100)
    private String idLoteUltimaAlteracao;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    @Column(name = "atualizado_por", length = 150)
    private String atualizadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
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

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(LocalDate saleDate) {
        this.saleDate = saleDate;
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

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTipoVenda() {
        return tipoVenda;
    }

    public void setTipoVenda(String tipoVenda) {
        this.tipoVenda = tipoVenda;
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

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(OffsetDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public void setAtualizadoPor(String atualizadoPor) {
        this.atualizadoPor = atualizadoPor;
    }
}

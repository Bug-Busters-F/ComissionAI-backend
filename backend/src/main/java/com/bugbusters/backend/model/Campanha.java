package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tb_campanha")
public class Campanha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(name = "texto_original", nullable = false, columnDefinition = "TEXT")
    private String textoOriginal;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstadoCampanha estado = EstadoCampanha.DRAFT;

    @Column(name = "removido_em")
    private OffsetDateTime removidoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    public Campanha() {}

    public Campanha(String titulo, String textoOriginal, LocalDate dataInicio, LocalDate dataFim) {
        this.titulo = titulo;
        this.textoOriginal = textoOriginal;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.estado = EstadoCampanha.DRAFT;
    }

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = OffsetDateTime.now();
        }
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getTextoOriginal() { return textoOriginal; }
    public void setTextoOriginal(String textoOriginal) { this.textoOriginal = textoOriginal; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }

    public EstadoCampanha getEstado() { return estado; }
    public void setEstado(EstadoCampanha estado) { this.estado = estado; }
    public void setEstado(String estado) {
        this.estado = estado != null ? EstadoCampanha.valueOf(estado) : EstadoCampanha.DRAFT;
    }

    public OffsetDateTime getRemovidoEm() { return removidoEm; }
    public void setRemovidoEm(OffsetDateTime removidoEm) { this.removidoEm = removidoEm; }
    
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }
    
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(OffsetDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
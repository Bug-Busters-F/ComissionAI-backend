package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tb_inconsistencia_importacao")
public class InconsistenciaImportacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "envio_id", nullable = false)
    private EnvioArquivo envio;

    @Column(nullable = false)
    private int linha;

    @Column(length = 100)
    private String campo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(nullable = false, length = 20)
    private String severidade;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    public InconsistenciaImportacao() {}

    public InconsistenciaImportacao(EnvioArquivo envio, int linha, String campo, String motivo, String severidade) {
        this.envio = envio;
        this.linha = linha;
        this.campo = campo;
        this.motivo = motivo;
        this.severidade = severidade;
        this.criadoEm = OffsetDateTime.now();
    }

    // Getters e Setters
    public Long getId() { return id; }

    public EnvioArquivo getEnvio() { return envio; }
    public void setEnvio(EnvioArquivo envio) { this.envio = envio; }

    public int getLinha() { return linha; }

    public String getCampo() { return campo; }

    public String getMotivo() { return motivo; }
    
    public String getSeveridade() { return severidade; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }
}
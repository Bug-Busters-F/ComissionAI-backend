package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Registro auditável e imutável de transições de estados e etapas na esteira de campanhas (BUG-99 / BACK-4).
 */
@Entity
@Table(name = "tb_campanha_esteira_transicao")
public class CampanhaEsteiraTransicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_versao_id", nullable = false)
    private CampanhaVersao campanhaVersao;

    @Column(name = "etapa_origem", nullable = false)
    private Integer etapaOrigem;

    @Column(name = "etapa_destino", nullable = false)
    private Integer etapaDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_origem", nullable = false, length = 30)
    private EstadoEsteiraCampanha estadoOrigem;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_destino", nullable = false, length = 30)
    private EstadoEsteiraCampanha estadoDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_acao", nullable = false, length = 40)
    private TipoAcaoEsteira tipoAcao;

    @Column(nullable = false, length = 150)
    private String responsavel;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    public CampanhaEsteiraTransicao() {
    }

    public CampanhaEsteiraTransicao(
            CampanhaVersao campanhaVersao,
            Integer etapaOrigem,
            Integer etapaDestino,
            EstadoEsteiraCampanha estadoOrigem,
            EstadoEsteiraCampanha estadoDestino,
            TipoAcaoEsteira tipoAcao,
            String responsavel,
            String motivo
    ) {
        this.campanhaVersao = campanhaVersao;
        this.etapaOrigem = etapaOrigem;
        this.etapaDestino = etapaDestino;
        this.estadoOrigem = estadoOrigem;
        this.estadoDestino = estadoDestino;
        this.tipoAcao = tipoAcao;
        this.responsavel = responsavel;
        this.motivo = motivo;
        this.criadoEm = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CampanhaVersao getCampanhaVersao() {
        return campanhaVersao;
    }

    public void setCampanhaVersao(CampanhaVersao campanhaVersao) {
        this.campanhaVersao = campanhaVersao;
    }

    public Integer getEtapaOrigem() {
        return etapaOrigem;
    }

    public void setEtapaOrigem(Integer etapaOrigem) {
        this.etapaOrigem = etapaOrigem;
    }

    public Integer getEtapaDestino() {
        return etapaDestino;
    }

    public void setEtapaDestino(Integer etapaDestino) {
        this.etapaDestino = etapaDestino;
    }

    public EstadoEsteiraCampanha getEstadoOrigem() {
        return estadoOrigem;
    }

    public void setEstadoOrigem(EstadoEsteiraCampanha estadoOrigem) {
        this.estadoOrigem = estadoOrigem;
    }

    public EstadoEsteiraCampanha getEstadoDestino() {
        return estadoDestino;
    }

    public void setEstadoDestino(EstadoEsteiraCampanha estadoDestino) {
        this.estadoDestino = estadoDestino;
    }

    public TipoAcaoEsteira getTipoAcao() {
        return tipoAcao;
    }

    public void setTipoAcao(TipoAcaoEsteira tipoAcao) {
        this.tipoAcao = tipoAcao;
    }

    public String getResponsavel() {
        return responsavel;
    }

    public void setResponsavel(String responsavel) {
        this.responsavel = responsavel;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(OffsetDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }
}


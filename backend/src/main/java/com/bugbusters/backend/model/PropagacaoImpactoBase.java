package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de Auditoria de Propagação de Impacto de Correções de Bases (BACK-6).
 */
@Entity
@Table(name = "tb_propagacao_impacto_base")
public class PropagacaoImpactoBase {

    @Id
    @Column(updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_preparacao_id", nullable = false, updatable = false)
    private LotePreparacao lotePreparacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_origem_id", nullable = false, updatable = false)
    private Campanha campanhaOrigem;

    @Column(name = "tipo_base", nullable = false, length = 20, updatable = false)
    private String tipoBase;

    @Column(name = "competencias_afetadas", length = 255, updatable = false)
    private String competenciasAfetadas;

    @Column(name = "qtd_campanhas_afetadas", nullable = false, updatable = false)
    private int qtdCampanhasAfetadas = 0;

    @Column(name = "qtd_regras_afetadas", nullable = false, updatable = false)
    private int qtdRegrasAfetadas = 0;

    @Column(name = "qtd_simulacoes_invalidadas", nullable = false, updatable = false)
    private int qtdSimulacoesInvalidadas = 0;

    @Column(name = "qtd_calculos_desatualizados", nullable = false, updatable = false)
    private int qtdCalculosDesatualizados = 0;

    @Column(name = "qtd_aprovacoes_codigo_invalidadas", nullable = false, updatable = false)
    private int qtdAprovacoesCodigoInvalidadas = 0;

    @Column(name = "detalhes_impacto_json", columnDefinition = "TEXT", updatable = false)
    private String detalhesImpactoJson;

    @Column(name = "confirmado_por", nullable = false, length = 150, updatable = false)
    private String confirmadoPor;

    @Column(name = "confirmado_em", nullable = false, updatable = false)
    private OffsetDateTime confirmadoEm;

    @Column(name = "motivo_confirmacao", columnDefinition = "TEXT", updatable = false)
    private String motivoConfirmacao;

    public PropagacaoImpactoBase() {}

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.confirmadoEm == null) {
            this.confirmadoEm = OffsetDateTime.now();
        }
    }

    public PropagacaoImpactoBase(LotePreparacao lotePreparacao, Campanha campanhaOrigem, String tipoBase,
                                 String competenciasAfetadas, int qtdCampanhasAfetadas, int qtdRegrasAfetadas,
                                 int qtdSimulacoesInvalidadas, int qtdCalculosDesatualizados,
                                 int qtdAprovacoesCodigoInvalidadas, String detalhesImpactoJson,
                                 String confirmadoPor, String motivoConfirmacao) {
        this.id = UUID.randomUUID();
        this.lotePreparacao = lotePreparacao;
        this.campanhaOrigem = campanhaOrigem;
        this.tipoBase = tipoBase;
        this.competenciasAfetadas = competenciasAfetadas;
        this.qtdCampanhasAfetadas = qtdCampanhasAfetadas;
        this.qtdRegrasAfetadas = qtdRegrasAfetadas;
        this.qtdSimulacoesInvalidadas = qtdSimulacoesInvalidadas;
        this.qtdCalculosDesatualizados = qtdCalculosDesatualizados;
        this.qtdAprovacoesCodigoInvalidadas = qtdAprovacoesCodigoInvalidadas;
        this.detalhesImpactoJson = detalhesImpactoJson;
        this.confirmadoPor = confirmadoPor != null ? confirmadoPor : "SISTEMA";
        this.confirmadoEm = OffsetDateTime.now();
        this.motivoConfirmacao = motivoConfirmacao;
    }

    public UUID getId() {
        return id;
    }

    public LotePreparacao getLotePreparacao() {
        return lotePreparacao;
    }

    public Campanha getCampanhaOrigem() {
        return campanhaOrigem;
    }

    public String getTipoBase() {
        return tipoBase;
    }

    public String getCompetenciasAfetadas() {
        return competenciasAfetadas;
    }

    public int getQtdCampanhasAfetadas() {
        return qtdCampanhasAfetadas;
    }

    public int getQtdRegrasAfetadas() {
        return qtdRegrasAfetadas;
    }

    public int getQtdSimulacoesInvalidadas() {
        return qtdSimulacoesInvalidadas;
    }

    public int getQtdCalculosDesatualizados() {
        return qtdCalculosDesatualizados;
    }

    public int getQtdAprovacoesCodigoInvalidadas() {
        return qtdAprovacoesCodigoInvalidadas;
    }

    public String getDetalhesImpactoJson() {
        return detalhesImpactoJson;
    }

    public String getConfirmadoPor() {
        return confirmadoPor;
    }

    public OffsetDateTime getConfirmadoEm() {
        return confirmadoEm;
    }

    public String getMotivoConfirmacao() {
        return motivoConfirmacao;
    }
}

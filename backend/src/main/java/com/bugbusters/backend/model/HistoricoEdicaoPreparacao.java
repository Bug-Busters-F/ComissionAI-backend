package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_historico_edicao_preparacao")
public class HistoricoEdicaoPreparacao {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "linha_preparacao_id", nullable = false)
    private LinhaPreparacao linhaPreparacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_preparacao_id", nullable = false)
    private LotePreparacao lotePreparacao;

    @Column(name = "numero_linha", nullable = false)
    private int numeroLinha;

    @Column(name = "versao_anterior", nullable = false)
    private long versaoAnterior;

    @Column(name = "versao_nova", nullable = false)
    private long versaoNova;

    @Column(nullable = false, length = 30)
    private String origem;

    @Column(name = "valores_anteriores_json", nullable = false, columnDefinition = "TEXT")
    private String valoresAnterioresJson;

    @Column(name = "valores_novos_json", nullable = false, columnDefinition = "TEXT")
    private String valoresNovosJson;

    @Column(name = "campos_alterados", nullable = false, length = 255)
    private String camposAlterados;

    @Column(name = "erros_antes_json", columnDefinition = "TEXT")
    private String errosAntesJson;

    @Column(name = "erros_depois_json", columnDefinition = "TEXT")
    private String errosDepoisJson;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "editado_por", nullable = false, length = 150)
    private String editadoPor;

    @Column(name = "editado_em", nullable = false, updatable = false)
    private OffsetDateTime editadoEm = OffsetDateTime.now();

    public HistoricoEdicaoPreparacao() {}

    public HistoricoEdicaoPreparacao(LinhaPreparacao linhaPreparacao, LotePreparacao lotePreparacao, int numeroLinha,
                                     long versaoAnterior, long versaoNova, String origem, String valoresAnterioresJson,
                                     String valoresNovosJson, String camposAlterados, String errosAntesJson,
                                     String errosDepoisJson, String motivo, String editadoPor) {
        this.id = UUID.randomUUID();
        this.linhaPreparacao = linhaPreparacao;
        this.lotePreparacao = lotePreparacao;
        this.numeroLinha = numeroLinha;
        this.versaoAnterior = versaoAnterior;
        this.versaoNova = versaoNova;
        this.origem = origem != null ? origem : "MANUAL";
        this.valoresAnterioresJson = valoresAnterioresJson != null ? valoresAnterioresJson : "{}";
        this.valoresNovosJson = valoresNovosJson != null ? valoresNovosJson : "{}";
        this.camposAlterados = camposAlterados != null ? camposAlterados : "";
        this.errosAntesJson = errosAntesJson;
        this.errosDepoisJson = errosDepoisJson;
        this.motivo = motivo;
        this.editadoPor = editadoPor != null ? editadoPor : "SISTEMA";
        this.editadoEm = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.editadoEm == null) {
            this.editadoEm = OffsetDateTime.now();
        }
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LinhaPreparacao getLinhaPreparacao() { return linhaPreparacao; }
    public void setLinhaPreparacao(LinhaPreparacao linhaPreparacao) { this.linhaPreparacao = linhaPreparacao; }

    public LotePreparacao getLotePreparacao() { return lotePreparacao; }
    public void setLotePreparacao(LotePreparacao lotePreparacao) { this.lotePreparacao = lotePreparacao; }

    public int getNumeroLinha() { return numeroLinha; }
    public void setNumeroLinha(int numeroLinha) { this.numeroLinha = numeroLinha; }

    public long getVersaoAnterior() { return versaoAnterior; }
    public void setVersaoAnterior(long versaoAnterior) { this.versaoAnterior = versaoAnterior; }

    public long getVersaoNova() { return versaoNova; }
    public void setVersaoNova(long versaoNova) { this.versaoNova = versaoNova; }

    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public String getValoresAnterioresJson() { return valoresAnterioresJson; }
    public void setValoresAnterioresJson(String valoresAnterioresJson) { this.valoresAnterioresJson = valoresAnterioresJson; }

    public String getValoresNovosJson() { return valoresNovosJson; }
    public void setValoresNovosJson(String valoresNovosJson) { this.valoresNovosJson = valoresNovosJson; }

    public String getCamposAlterados() { return camposAlterados; }
    public void setCamposAlterados(String camposAlterados) { this.camposAlterados = camposAlterados; }

    public String getErrosAntesJson() { return errosAntesJson; }
    public void setErrosAntesJson(String errosAntesJson) { this.errosAntesJson = errosAntesJson; }

    public String getErrosDepoisJson() { return errosDepoisJson; }
    public void setErrosDepoisJson(String errosDepoisJson) { this.errosDepoisJson = errosDepoisJson; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getEditadoPor() { return editadoPor; }
    public void setEditadoPor(String editadoPor) { this.editadoPor = editadoPor; }

    public OffsetDateTime getEditadoEm() { return editadoEm; }
    public void setEditadoEm(OffsetDateTime editadoEm) { this.editadoEm = editadoEm; }
}

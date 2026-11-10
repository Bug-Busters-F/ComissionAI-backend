package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_historico_versao_registro",
       uniqueConstraints = @UniqueConstraint(name = "uk_hist_versao_registro", columnNames = {"tipo_base", "registro_id", "versao"}))
public class HistoricoVersaoRegistro {

    @Id
    private UUID id;

    @Column(name = "tipo_base", nullable = false, length = 20)
    private String tipoBase;

    @Column(name = "registro_id", nullable = false)
    private UUID registroId;

    @Column(name = "chave_negocio", nullable = false, length = 255)
    private String chaveNegocio;

    @Column(nullable = false)
    private int versao;

    @Column(name = "tipo_operacao", nullable = false, length = 30)
    private String tipoOperacao;

    @Column(nullable = false, length = 30)
    private String origem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_id")
    private Campanha campanha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_preparacao_id")
    private LotePreparacao lotePreparacao;

    @Column(name = "id_lote", length = 100)
    private String idLote;

    @Column(name = "valores_anteriores_json", columnDefinition = "TEXT")
    private String valoresAnterioresJson;

    @Column(name = "valores_novos_json", nullable = false, columnDefinition = "TEXT")
    private String valoresNovosJson;

    @Column(name = "registrado_por", nullable = false, length = 150)
    private String registradoPor;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private OffsetDateTime registradoEm = OffsetDateTime.now();

    public HistoricoVersaoRegistro() {}

    public HistoricoVersaoRegistro(String tipoBase, UUID registroId, String chaveNegocio, int versao,
                                   String tipoOperacao, String origem, Campanha campanha,
                                   LotePreparacao lotePreparacao, String idLote,
                                   String valoresAnterioresJson, String valoresNovosJson,
                                   String registradoPor) {
        this.id = UUID.randomUUID();
        this.tipoBase = tipoBase;
        this.registroId = registroId;
        this.chaveNegocio = chaveNegocio;
        this.versao = versao;
        this.tipoOperacao = tipoOperacao;
        this.origem = origem != null ? origem : "IMPORTACAO";
        this.campanha = campanha;
        this.lotePreparacao = lotePreparacao;
        this.idLote = idLote;
        this.valoresAnterioresJson = valoresAnterioresJson;
        this.valoresNovosJson = valoresNovosJson != null ? valoresNovosJson : "{}";
        this.registradoPor = registradoPor != null ? registradoPor : "SISTEMA";
        this.registradoEm = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.registradoEm == null) {
            this.registradoEm = OffsetDateTime.now();
        }
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTipoBase() { return tipoBase; }
    public void setTipoBase(String tipoBase) { this.tipoBase = tipoBase; }

    public UUID getRegistroId() { return registroId; }
    public void setRegistroId(UUID registroId) { this.registroId = registroId; }

    public String getChaveNegocio() { return chaveNegocio; }
    public void setChaveNegocio(String chaveNegocio) { this.chaveNegocio = chaveNegocio; }

    public int getVersao() { return versao; }
    public void setVersao(int versao) { this.versao = versao; }

    public String getTipoOperacao() { return tipoOperacao; }
    public void setTipoOperacao(String tipoOperacao) { this.tipoOperacao = tipoOperacao; }

    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public Campanha getCampanha() { return campanha; }
    public void setCampanha(Campanha campanha) { this.campanha = campanha; }

    public LotePreparacao getLotePreparacao() { return lotePreparacao; }
    public void setLotePreparacao(LotePreparacao lotePreparacao) { this.lotePreparacao = lotePreparacao; }

    public String getIdLote() { return idLote; }
    public void setIdLote(String idLote) { this.idLote = idLote; }

    public String getValoresAnterioresJson() { return valoresAnterioresJson; }
    public void setValoresAnterioresJson(String valoresAnterioresJson) { this.valoresAnterioresJson = valoresAnterioresJson; }

    public String getValoresNovosJson() { return valoresNovosJson; }
    public void setValoresNovosJson(String valoresNovosJson) { this.valoresNovosJson = valoresNovosJson; }

    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }

    public OffsetDateTime getRegistradoEm() { return registradoEm; }
    public void setRegistradoEm(OffsetDateTime registradoEm) { this.registradoEm = registradoEm; }
}

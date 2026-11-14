package com.bugbusters.backend.model;

import com.bugbusters.backend.sales.Sale;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_alerta_estatistico")
public class AlertaEstatistico {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lote_preparacao_id") private LotePreparacao lotePreparacao;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "venda_id") private Sale venda;
    @Column(length = 100) private String matricula;
    @Column(name = "data_referencia") private LocalDate dataReferencia;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private TipoAlertaEstatistico tipo;
    @Column(nullable = false, length = 40) private String metodo;
    @Column(name = "grupo_comparavel", nullable = false, length = 300) private String grupoComparavel;
    @Column(name = "tamanho_amostra", nullable = false) private int tamanhoAmostra;
    @Column(name = "valor_observado", precision = 15, scale = 2) private BigDecimal valorObservado;
    @Column(name = "media_grupo", precision = 15, scale = 2) private BigDecimal mediaGrupo;
    @Column(name = "desvio_padrao", precision = 15, scale = 4) private BigDecimal desvioPadrao;
    @Column(precision = 15, scale = 6) private BigDecimal score;
    @Column(name = "versao_bases", nullable = false, length = 64) private String versaoBases;
    @Column(nullable = false, length = 64) private String assinatura;
    @Column(name = "gerado_em", nullable = false) private OffsetDateTime geradoEm = OffsetDateTime.now();
    @Column(name = "substituido_em") private OffsetDateTime substituidoEm;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "substituido_por_id") private AlertaEstatistico substituidoPor;

    @PrePersist void preparar() { if (id == null) id = UUID.randomUUID(); if (geradoEm == null) geradoEm = OffsetDateTime.now(); }
    public UUID getId() { return id; } public LotePreparacao getLotePreparacao() { return lotePreparacao; } public void setLotePreparacao(LotePreparacao v) { lotePreparacao = v; }
    public Sale getVenda() { return venda; } public void setVenda(Sale v) { venda = v; } public String getMatricula() { return matricula; } public void setMatricula(String v) { matricula = v; }
    public LocalDate getDataReferencia() { return dataReferencia; } public void setDataReferencia(LocalDate v) { dataReferencia = v; } public TipoAlertaEstatistico getTipo() { return tipo; } public void setTipo(TipoAlertaEstatistico v) { tipo = v; }
    public String getMetodo() { return metodo; } public void setMetodo(String v) { metodo = v; } public String getGrupoComparavel() { return grupoComparavel; } public void setGrupoComparavel(String v) { grupoComparavel = v; }
    public int getTamanhoAmostra() { return tamanhoAmostra; } public void setTamanhoAmostra(int v) { tamanhoAmostra = v; } public BigDecimal getValorObservado() { return valorObservado; } public void setValorObservado(BigDecimal v) { valorObservado = v; }
    public BigDecimal getMediaGrupo() { return mediaGrupo; } public void setMediaGrupo(BigDecimal v) { mediaGrupo = v; } public BigDecimal getDesvioPadrao() { return desvioPadrao; } public void setDesvioPadrao(BigDecimal v) { desvioPadrao = v; }
    public BigDecimal getScore() { return score; } public void setScore(BigDecimal v) { score = v; } public String getVersaoBases() { return versaoBases; } public void setVersaoBases(String v) { versaoBases = v; }
    public String getAssinatura() { return assinatura; } public void setAssinatura(String v) { assinatura = v; } public OffsetDateTime getGeradoEm() { return geradoEm; }
    public OffsetDateTime getSubstituidoEm() { return substituidoEm; } public AlertaEstatistico getSubstituidoPor() { return substituidoPor; }
    public void substituirPor(AlertaEstatistico novo) { substituidoEm = OffsetDateTime.now(); substituidoPor = novo; }
    public void substituir() { substituidoEm = OffsetDateTime.now(); }
}

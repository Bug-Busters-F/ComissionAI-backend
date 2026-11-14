package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de Snapshot de Resultado de Cálculo Preservado (BACK-6).
 * <p>
 * Preserva o resultado de cálculo histórico quando a base é corrigida ou quando
 * uma nova execução identificada substitui o cálculo anterior.
 * </p>
 */
@Entity
@Table(name = "tb_snapshot_resultado_calculo")
public class SnapshotResultadoCalculo {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "resultado_calculo_id", nullable = false, updatable = false)
    private Long resultadoCalculoId;

    @Column(name = "protocolo_calculo", nullable = false, updatable = false)
    private UUID protocoloCalculo;

    @Column(name = "id_venda", nullable = false, updatable = false)
    private UUID idVenda;

    @Column(nullable = false, length = 50, updatable = false)
    private String matricula;

    @Column(name = "cod_marca", updatable = false)
    private Integer codMarca;

    @Column(name = "cod_loja", updatable = false)
    private Integer codLoja;

    @Column(name = "cod_cargo", updatable = false)
    private Integer codCargo;

    @Column(name = "regra_id", nullable = false, updatable = false)
    private Long regraId;

    @Column(name = "origem_taxa", length = 30, updatable = false)
    private String origemTaxa;

    @Column(name = "data_venda", nullable = false, updatable = false)
    private LocalDate dataVenda;

    @Column(name = "valor_venda", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valorVenda;

    @Column(name = "taxa_aplicada", nullable = false, precision = 6, scale = 4, updatable = false)
    private BigDecimal taxaAplicada;

    @Column(name = "valor_comissao", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valorComissao;

    @Column(name = "valor_bonus_percentual", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valorBonusPercentual = BigDecimal.ZERO;

    @Column(name = "valor_bonus_fixo", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valorBonusFixo = BigDecimal.ZERO;

    @Column(name = "versao_campanha_numero", updatable = false)
    private Integer versaoCampanhaNumero;

    @Column(name = "fechamento_id", updatable = false)
    private UUID fechamentoId;

    @Column(name = "tipo_calculo", length = 50, updatable = false)
    private String tipoCalculo;

    @Column(name = "calculado_em", nullable = false, updatable = false)
    private OffsetDateTime calculadoEm;

    @Column(name = "preservado_em", nullable = false, updatable = false)
    private OffsetDateTime preservadoEm;

    @Column(name = "motivo_preservacao", columnDefinition = "TEXT", updatable = false)
    private String motivoPreservacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_preparacao_id", updatable = false)
    private LotePreparacao lotePreparacao;

    @Column(name = "substituido_por_protocolo")
    private UUID substituidoPorProtocolo;

    public SnapshotResultadoCalculo() {}

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.preservadoEm == null) {
            this.preservadoEm = OffsetDateTime.now();
        }
    }

    public SnapshotResultadoCalculo(ResultadoCalculo resultado, String motivoPreservacao, LotePreparacao lotePreparacao) {
        this.id = UUID.randomUUID();
        this.resultadoCalculoId = resultado.getId();
        this.protocoloCalculo = resultado.getProtocoloCalculo();
        this.idVenda = resultado.getIdVenda();
        this.matricula = resultado.getMatricula();
        this.codMarca = resultado.getCodMarca();
        this.codLoja = resultado.getCodLoja();
        this.codCargo = resultado.getCodCargo();
        this.regraId = resultado.getRegraId();
        this.origemTaxa = resultado.getOrigemTaxa();
        this.dataVenda = resultado.getDataVenda();
        this.valorVenda = resultado.getValorVenda();
        this.taxaAplicada = resultado.getTaxaAplicada();
        this.valorComissao = resultado.getValorComissao();
        this.valorBonusPercentual = resultado.getValorBonusPercentual();
        this.valorBonusFixo = resultado.getValorBonusFixo();
        this.versaoCampanhaNumero = resultado.getVersaoCampanhaNumero();
        this.fechamentoId = resultado.getFechamentoId();
        this.tipoCalculo = resultado.getTipoCalculo();
        this.calculadoEm = resultado.getCalculadoEm();
        this.preservadoEm = OffsetDateTime.now();
        this.motivoPreservacao = motivoPreservacao;
        this.lotePreparacao = lotePreparacao;
    }

    public UUID getId() {
        return id;
    }

    public Long getResultadoCalculoId() {
        return resultadoCalculoId;
    }

    public UUID getProtocoloCalculo() {
        return protocoloCalculo;
    }

    public UUID getIdVenda() {
        return idVenda;
    }

    public String getMatricula() {
        return matricula;
    }

    public Integer getCodMarca() {
        return codMarca;
    }

    public Integer getCodLoja() {
        return codLoja;
    }

    public Integer getCodCargo() {
        return codCargo;
    }

    public Long getRegraId() {
        return regraId;
    }

    public String getOrigemTaxa() {
        return origemTaxa;
    }

    public LocalDate getDataVenda() {
        return dataVenda;
    }

    public BigDecimal getValorVenda() {
        return valorVenda;
    }

    public BigDecimal getTaxaAplicada() {
        return taxaAplicada;
    }

    public BigDecimal getValorComissao() {
        return valorComissao;
    }

    public BigDecimal getValorBonusPercentual() {
        return valorBonusPercentual;
    }

    public BigDecimal getValorBonusFixo() {
        return valorBonusFixo;
    }

    public Integer getVersaoCampanhaNumero() {
        return versaoCampanhaNumero;
    }

    public UUID getFechamentoId() {
        return fechamentoId;
    }

    public String getTipoCalculo() {
        return tipoCalculo;
    }

    public OffsetDateTime getCalculadoEm() {
        return calculadoEm;
    }

    public OffsetDateTime getPreservadoEm() {
        return preservadoEm;
    }

    public String getMotivoPreservacao() {
        return motivoPreservacao;
    }

    public LotePreparacao getLotePreparacao() {
        return lotePreparacao;
    }

    public UUID getSubstituidoPorProtocolo() {
        return substituidoPorProtocolo;
    }

    public void setSubstituidoPorProtocolo(UUID substituidoPorProtocolo) {
        this.substituidoPorProtocolo = substituidoPorProtocolo;
    }
}

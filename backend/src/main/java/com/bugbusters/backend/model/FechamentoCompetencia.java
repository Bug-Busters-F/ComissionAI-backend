package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de persistência do Fechamento Final de Competência (BACK-11).
 * <p>
 * Registra a liquidação formal da competência, com data, protocolo de execução,
 * totais consolidados e controle de estado para bloqueio de mutações em campanhas e regras.
 * </p>
 */
@Entity
@Table(name = "tb_fechamento_competencia")
public class FechamentoCompetencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 7)
    private String competencia;

    @Column(nullable = false, unique = true)
    private UUID protocolo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusFechamento status = StatusFechamento.PROCESSANDO;

    @Column(nullable = false, length = 30)
    private String tipo = "FINAL";

    @Column(name = "total_vendas_processadas", nullable = false)
    private int totalVendasProcessadas = 0;

    @Column(name = "total_calculados", nullable = false)
    private int totalCalculados = 0;

    @Column(name = "total_impedimentos", nullable = false)
    private int totalImpedimentos = 0;

    @Column(name = "valor_total_vendas", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotalVendas = BigDecimal.ZERO;

    @Column(name = "valor_total_comissao", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotalComissao = BigDecimal.ZERO;

    @Column(name = "executado_por", length = 150)
    private String executadoPor;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "iniciado_em", nullable = false)
    private OffsetDateTime iniciadoEm = OffsetDateTime.now();

    @Column(name = "concluido_em")
    private OffsetDateTime concluidoEm;

    @Column(name = "motivo_falha", columnDefinition = "TEXT")
    private String motivoFalha;

    public FechamentoCompetencia() {}

    public FechamentoCompetencia(String competencia, UUID protocolo, String executadoPor, String motivo) {
        this.competencia = competencia;
        this.protocolo = protocolo != null ? protocolo : UUID.randomUUID();
        this.executadoPor = executadoPor;
        this.motivo = motivo;
        this.status = StatusFechamento.PROCESSANDO;
        this.tipo = "FINAL";
        this.iniciadoEm = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.protocolo == null) {
            this.protocolo = UUID.randomUUID();
        }
        if (this.iniciadoEm == null) {
            this.iniciadoEm = OffsetDateTime.now();
        }
        if (this.tipo == null) {
            this.tipo = "FINAL";
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public UUID getProtocolo() { return protocolo; }
    public void setProtocolo(UUID protocolo) { this.protocolo = protocolo; }

    public StatusFechamento getStatus() { return status; }
    public void setStatus(StatusFechamento status) { this.status = status; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public int getTotalVendasProcessadas() { return totalVendasProcessadas; }
    public void setTotalVendasProcessadas(int totalVendasProcessadas) { this.totalVendasProcessadas = totalVendasProcessadas; }

    public int getTotalCalculados() { return totalCalculados; }
    public void setTotalCalculados(int totalCalculados) { this.totalCalculados = totalCalculados; }

    public int getTotalImpedimentos() { return totalImpedimentos; }
    public void setTotalImpedimentos(int totalImpedimentos) { this.totalImpedimentos = totalImpedimentos; }

    public BigDecimal getValorTotalVendas() { return valorTotalVendas; }
    public void setValorTotalVendas(BigDecimal valorTotalVendas) { this.valorTotalVendas = valorTotalVendas; }

    public BigDecimal getValorTotalComissao() { return valorTotalComissao; }
    public void setValorTotalComissao(BigDecimal valorTotalComissao) { this.valorTotalComissao = valorTotalComissao; }

    public String getExecutadoPor() { return executadoPor; }
    public void setExecutadoPor(String executadoPor) { this.executadoPor = executadoPor; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public OffsetDateTime getIniciadoEm() { return iniciadoEm; }
    public void setIniciadoEm(OffsetDateTime iniciadoEm) { this.iniciadoEm = iniciadoEm; }

    public OffsetDateTime getConcluidoEm() { return concluidoEm; }
    public void setConcluidoEm(OffsetDateTime concluidoEm) { this.concluidoEm = concluidoEm; }

    public String getMotivoFalha() { return motivoFalha; }
    public void setMotivoFalha(String motivoFalha) { this.motivoFalha = motivoFalha; }
}

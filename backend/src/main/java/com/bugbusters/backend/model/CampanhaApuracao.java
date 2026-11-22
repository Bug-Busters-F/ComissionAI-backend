package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade que registra a apuração definitiva de comissões por campanha (BUG-109 / BACK-14).
 * <p>
 * Representa a consolidação financeira de vendas processadas pelas regras e programa Python
 * aprovados da campanha, com auditoria completa de volumes, totais e versionamento.
 * </p>
 */
@Entity
@Table(name = "tb_campanha_apuracao")
public class CampanhaApuracao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_id", nullable = false)
    private Campanha campanha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_versao_id")
    private CampanhaVersao campanhaVersao;

    @Column(nullable = false, unique = true)
    private UUID protocolo;

    @Column(length = 7)
    private String competencia;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "versao_campanha_numero", nullable = false)
    private Integer versaoCampanhaNumero;

    @Column(name = "versao_programa_python")
    private Integer versaoProgramaPython;

    @Column(name = "checksum_python", length = 64)
    private String checksumPython;

    @Column(name = "executado_via_python", nullable = false)
    private boolean executadoViaPython = false;

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

    @Column(name = "valor_total_bonus", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotalBonus = BigDecimal.ZERO;

    @Column(name = "executado_por", nullable = false, length = 150)
    private String executadoPor;

    @Column(name = "executado_em", nullable = false)
    private OffsetDateTime executadoEm = OffsetDateTime.now();

    @Column(nullable = false, length = 30)
    private String status = "CONCLUIDO";

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    public CampanhaApuracao() {}

    @PrePersist
    public void prePersist() {
        if (this.protocolo == null) {
            this.protocolo = UUID.randomUUID();
        }
        if (this.executadoEm == null) {
            this.executadoEm = OffsetDateTime.now();
        }
        if (this.status == null) {
            this.status = "CONCLUIDO";
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Campanha getCampanha() {
        return campanha;
    }

    public void setCampanha(Campanha campanha) {
        this.campanha = campanha;
    }

    public CampanhaVersao getCampanhaVersao() {
        return campanhaVersao;
    }

    public void setCampanhaVersao(CampanhaVersao campanhaVersao) {
        this.campanhaVersao = campanhaVersao;
    }

    public UUID getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(UUID protocolo) {
        this.protocolo = protocolo;
    }

    public String getCompetencia() {
        return competencia;
    }

    public void setCompetencia(String competencia) {
        this.competencia = competencia;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
    }

    public Integer getVersaoCampanhaNumero() {
        return versaoCampanhaNumero;
    }

    public void setVersaoCampanhaNumero(Integer versaoCampanhaNumero) {
        this.versaoCampanhaNumero = versaoCampanhaNumero;
    }

    public Integer getVersaoProgramaPython() {
        return versaoProgramaPython;
    }

    public void setVersaoProgramaPython(Integer versaoProgramaPython) {
        this.versaoProgramaPython = versaoProgramaPython;
    }

    public String getChecksumPython() {
        return checksumPython;
    }

    public void setChecksumPython(String checksumPython) {
        this.checksumPython = checksumPython;
    }

    public boolean isExecutadoViaPython() {
        return executadoViaPython;
    }

    public void setExecutadoViaPython(boolean executadoViaPython) {
        this.executadoViaPython = executadoViaPython;
    }

    public int getTotalVendasProcessadas() {
        return totalVendasProcessadas;
    }

    public void setTotalVendasProcessadas(int totalVendasProcessadas) {
        this.totalVendasProcessadas = totalVendasProcessadas;
    }

    public int getTotalCalculados() {
        return totalCalculados;
    }

    public void setTotalCalculados(int totalCalculados) {
        this.totalCalculados = totalCalculados;
    }

    public int getTotalImpedimentos() {
        return totalImpedimentos;
    }

    public void setTotalImpedimentos(int totalImpedimentos) {
        this.totalImpedimentos = totalImpedimentos;
    }

    public BigDecimal getValorTotalVendas() {
        return valorTotalVendas;
    }

    public void setValorTotalVendas(BigDecimal valorTotalVendas) {
        this.valorTotalVendas = valorTotalVendas != null ? valorTotalVendas : BigDecimal.ZERO;
    }

    public BigDecimal getValorTotalComissao() {
        return valorTotalComissao;
    }

    public void setValorTotalComissao(BigDecimal valorTotalComissao) {
        this.valorTotalComissao = valorTotalComissao != null ? valorTotalComissao : BigDecimal.ZERO;
    }

    public BigDecimal getValorTotalBonus() {
        return valorTotalBonus;
    }

    public void setValorTotalBonus(BigDecimal valorTotalBonus) {
        this.valorTotalBonus = valorTotalBonus != null ? valorTotalBonus : BigDecimal.ZERO;
    }

    public String getExecutadoPor() {
        return executadoPor;
    }

    public void setExecutadoPor(String executadoPor) {
        this.executadoPor = executadoPor;
    }

    public OffsetDateTime getExecutadoEm() {
        return executadoEm;
    }

    public void setExecutadoEm(OffsetDateTime executadoEm) {
        this.executadoEm = executadoEm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }
}


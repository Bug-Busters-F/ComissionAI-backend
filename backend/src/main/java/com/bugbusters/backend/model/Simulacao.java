package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_simulacao")
public class Simulacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID protocolo;

    @Column(name = "executado_em", nullable = false)
    private OffsetDateTime executadoEm = OffsetDateTime.now();

    @Column(nullable = false, length = 10)
    private String competencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_id")
    private Campanha campanha;

    @Column(name = "titulo_campanha")
    private String tituloCampanha;

    @Column(name = "data_inicio_campanha", nullable = false)
    private LocalDate dataInicioCampanha;

    @Column(name = "data_fim_campanha", nullable = false)
    private LocalDate dataFimCampanha;

    @Column(name = "dias_inclusivos", nullable = false)
    private long diasInclusivos;

    @Column(name = "fator_projecao", nullable = false, precision = 10, scale = 6)
    private BigDecimal fatorProjecao;

    @Column(name = "origem_dados", nullable = false, length = 100)
    private String origemDados = "HISTORICO_VENDAS_COMPETENCIA";

    @Enumerated(EnumType.STRING)
    @Column(name = "status_aceite", nullable = false, length = 30)
    private StatusAceiteSimulacao statusAceite = StatusAceiteSimulacao.PENDENTE;

    @Column(name = "aceito_em")
    private OffsetDateTime aceitoEm;

    @Column(name = "aceito_por", length = 150)
    private String aceitoPor;

    @Column(name = "motivo_aceite", columnDefinition = "TEXT")
    private String motivoAceite;

    @Column(name = "hash_proposta", length = 64)
    private String hashProposta;

    @Column(name = "invalidado_em")
    private OffsetDateTime invalidadoEm;

    @Column(name = "motivo_invalidacao", columnDefinition = "TEXT")
    private String motivoInvalidacao;

    @Column(name = "premissas_snapshot", nullable = false, columnDefinition = "TEXT")
    private String premissasSnapshot;

    @Column(name = "memoria_calculo_consolidada", columnDefinition = "TEXT")
    private String memoriaCalculoConsolidada;

    @Column(name = "total_vendas_recortadas", nullable = false)
    private int totalVendasRecortadas;

    @Column(name = "valor_total_recortado", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotalRecortado;

    // --- Cenário 100% (Proposta Revisada vs Base) ---
    @Column(name = "total_comissao_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalComissaoBase;

    @Column(name = "total_comissao_proposta", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalComissaoProposta;

    @Column(name = "delta_comissao", nullable = false, precision = 15, scale = 2)
    private BigDecimal deltaComissao;

    @Column(name = "delta_percentual", nullable = false, precision = 10, scale = 2)
    private BigDecimal deltaPercentual;

    @Column(name = "taxa_media_efetiva_base", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaMediaEfetivaBase;

    @Column(name = "taxa_media_efetiva_proposta", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaMediaEfetivaProposta;

    @Column(name = "comissao_projetada", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissaoProjetada;

    @Column(name = "delta_projetado", nullable = false, precision = 15, scale = 2)
    private BigDecimal deltaProjetado;

    @Column(name = "qtd_vendas_com_mudanca_faixa", nullable = false)
    private int qtdVendasComMudancaFaixa;

    @Column(name = "qtd_vendas_com_regra_aplicada", nullable = false)
    private int qtdVendasComRegraAplicada;

    @Column(name = "qtd_vendas_com_fallback_base", nullable = false)
    private int qtdVendasComFallbackBase;

    @Column(name = "qtd_vendas_com_impedimento", nullable = false)
    private int qtdVendasComImpedimento;

    // --- Cenário 80% (Volume Reduzido) ---
    @Column(name = "vendas_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal vendas80;

    @Column(name = "comissao_base_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissaoBase80;

    @Column(name = "comissao_proposta_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissaoProposta80;

    @Column(name = "delta_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal delta80;

    @Column(name = "delta_perc_80", nullable = false, precision = 10, scale = 2)
    private BigDecimal deltaPerc80;

    @Column(name = "taxa_media_80", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaMedia80;

    @Column(name = "projetada_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal projetada80;

    @Column(name = "delta_proj_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal deltaProj80;

    @Column(name = "mudanca_faixa_80", nullable = false)
    private int mudancaFaixa80;

    // --- Cenário 120% (Crescimento Otimista) ---
    @Column(name = "vendas_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal vendas120;

    @Column(name = "comissao_base_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissaoBase120;

    @Column(name = "comissao_proposta_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissaoProposta120;

    @Column(name = "delta_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal delta120;

    @Column(name = "delta_perc_120", nullable = false, precision = 10, scale = 2)
    private BigDecimal deltaPerc120;

    @Column(name = "taxa_media_120", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaMedia120;

    @Column(name = "projetada_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal projetada120;

    @Column(name = "delta_proj_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal deltaProj120;

    @Column(name = "mudanca_faixa_120", nullable = false)
    private int mudancaFaixa120;

    // --- Cenário 150% (Alta Performance) ---
    @Column(name = "vendas_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal vendas150;

    @Column(name = "comissao_base_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissaoBase150;

    @Column(name = "comissao_proposta_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissaoProposta150;

    @Column(name = "delta_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal delta150;

    @Column(name = "delta_perc_150", nullable = false, precision = 10, scale = 2)
    private BigDecimal deltaPerc150;

    @Column(name = "taxa_media_150", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaMedia150;

    @Column(name = "projetada_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal projetada150;

    @Column(name = "delta_proj_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal deltaProj150;

    @Column(name = "mudanca_faixa_150", nullable = false)
    private int mudancaFaixa150;

    @OneToMany(mappedBy = "simulacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SimulacaoVenda> vendas = new ArrayList<>();

    public Simulacao() {}

    @PrePersist
    public void prePersist() {
        if (this.protocolo == null) {
            this.protocolo = UUID.randomUUID();
        }
        if (this.executadoEm == null) {
            this.executadoEm = OffsetDateTime.now();
        }
    }

    public void adicionarVenda(SimulacaoVenda venda) {
        if (venda != null) {
            vendas.add(venda);
            venda.setSimulacao(this);
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProtocolo() { return protocolo; }
    public void setProtocolo(UUID protocolo) { this.protocolo = protocolo; }

    public OffsetDateTime getExecutadoEm() { return executadoEm; }
    public void setExecutadoEm(OffsetDateTime executadoEm) { this.executadoEm = executadoEm; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public Campanha getCampanha() { return campanha; }
    public void setCampanha(Campanha campanha) { this.campanha = campanha; }

    public String getTituloCampanha() { return tituloCampanha; }
    public void setTituloCampanha(String tituloCampanha) { this.tituloCampanha = tituloCampanha; }

    public LocalDate getDataInicioCampanha() { return dataInicioCampanha; }
    public void setDataInicioCampanha(LocalDate dataInicioCampanha) { this.dataInicioCampanha = dataInicioCampanha; }

    public LocalDate getDataFimCampanha() { return dataFimCampanha; }
    public void setDataFimCampanha(LocalDate dataFimCampanha) { this.dataFimCampanha = dataFimCampanha; }

    public long getDiasInclusivos() { return diasInclusivos; }
    public void setDiasInclusivos(long diasInclusivos) { this.diasInclusivos = diasInclusivos; }

    public BigDecimal getFatorProjecao() { return fatorProjecao; }
    public void setFatorProjecao(BigDecimal fatorProjecao) { this.fatorProjecao = fatorProjecao; }

    public String getOrigemDados() { return origemDados; }
    public void setOrigemDados(String origemDados) { this.origemDados = origemDados; }

    public String getPremissasSnapshot() { return premissasSnapshot; }
    public void setPremissasSnapshot(String premissasSnapshot) { this.premissasSnapshot = premissasSnapshot; }

    public String getMemoriaCalculoConsolidada() { return memoriaCalculoConsolidada; }
    public void setMemoriaCalculoConsolidada(String memoriaCalculoConsolidada) { this.memoriaCalculoConsolidada = memoriaCalculoConsolidada; }

    public int getTotalVendasRecortadas() { return totalVendasRecortadas; }
    public void setTotalVendasRecortadas(int totalVendasRecortadas) { this.totalVendasRecortadas = totalVendasRecortadas; }

    public BigDecimal getValorTotalRecortado() { return valorTotalRecortado; }
    public void setValorTotalRecortado(BigDecimal valorTotalRecortado) { this.valorTotalRecortado = valorTotalRecortado; }

    public BigDecimal getTotalComissaoBase() { return totalComissaoBase; }
    public void setTotalComissaoBase(BigDecimal totalComissaoBase) { this.totalComissaoBase = totalComissaoBase; }

    public BigDecimal getTotalComissaoProposta() { return totalComissaoProposta; }
    public void setTotalComissaoProposta(BigDecimal totalComissaoProposta) { this.totalComissaoProposta = totalComissaoProposta; }

    public BigDecimal getDeltaComissao() { return deltaComissao; }
    public void setDeltaComissao(BigDecimal deltaComissao) { this.deltaComissao = deltaComissao; }

    public BigDecimal getDeltaPercentual() { return deltaPercentual; }
    public void setDeltaPercentual(BigDecimal deltaPercentual) { this.deltaPercentual = deltaPercentual; }

    public BigDecimal getTaxaMediaEfetivaBase() { return taxaMediaEfetivaBase; }
    public void setTaxaMediaEfetivaBase(BigDecimal taxaMediaEfetivaBase) { this.taxaMediaEfetivaBase = taxaMediaEfetivaBase; }

    public BigDecimal getTaxaMediaEfetivaProposta() { return taxaMediaEfetivaProposta; }
    public void setTaxaMediaEfetivaProposta(BigDecimal taxaMediaEfetivaProposta) { this.taxaMediaEfetivaProposta = taxaMediaEfetivaProposta; }

    public BigDecimal getComissaoProjetada() { return comissaoProjetada; }
    public void setComissaoProjetada(BigDecimal comissaoProjetada) { this.comissaoProjetada = comissaoProjetada; }

    public BigDecimal getDeltaProjetado() { return deltaProjetado; }
    public void setDeltaProjetado(BigDecimal deltaProjetado) { this.deltaProjetado = deltaProjetado; }

    public int getQtdVendasComMudancaFaixa() { return qtdVendasComMudancaFaixa; }
    public void setQtdVendasComMudancaFaixa(int qtdVendasComMudancaFaixa) { this.qtdVendasComMudancaFaixa = qtdVendasComMudancaFaixa; }

    public int getQtdVendasComRegraAplicada() { return qtdVendasComRegraAplicada; }
    public void setQtdVendasComRegraAplicada(int qtdVendasComRegraAplicada) { this.qtdVendasComRegraAplicada = qtdVendasComRegraAplicada; }

    public int getQtdVendasComFallbackBase() { return qtdVendasComFallbackBase; }
    public void setQtdVendasComFallbackBase(int qtdVendasComFallbackBase) { this.qtdVendasComFallbackBase = qtdVendasComFallbackBase; }

    public int getQtdVendasComImpedimento() { return qtdVendasComImpedimento; }
    public void setQtdVendasComImpedimento(int qtdVendasComImpedimento) { this.qtdVendasComImpedimento = qtdVendasComImpedimento; }

    public BigDecimal getVendas80() { return vendas80; }
    public void setVendas80(BigDecimal vendas80) { this.vendas80 = vendas80; }

    public BigDecimal getComissaoBase80() { return comissaoBase80; }
    public void setComissaoBase80(BigDecimal comissaoBase80) { this.comissaoBase80 = comissaoBase80; }

    public BigDecimal getComissaoProposta80() { return comissaoProposta80; }
    public void setComissaoProposta80(BigDecimal comissaoProposta80) { this.comissaoProposta80 = comissaoProposta80; }

    public BigDecimal getDelta80() { return delta80; }
    public void setDelta80(BigDecimal delta80) { this.delta80 = delta80; }

    public BigDecimal getDeltaPerc80() { return deltaPerc80; }
    public void setDeltaPerc80(BigDecimal deltaPerc80) { this.deltaPerc80 = deltaPerc80; }

    public BigDecimal getTaxaMedia80() { return taxaMedia80; }
    public void setTaxaMedia80(BigDecimal taxaMedia80) { this.taxaMedia80 = taxaMedia80; }

    public BigDecimal getProjetada80() { return projetada80; }
    public void setProjetada80(BigDecimal projetada80) { this.projetada80 = projetada80; }

    public BigDecimal getDeltaProj80() { return deltaProj80; }
    public void setDeltaProj80(BigDecimal deltaProj80) { this.deltaProj80 = deltaProj80; }

    public int getMudancaFaixa80() { return mudancaFaixa80; }
    public void setMudancaFaixa80(int mudancaFaixa80) { this.mudancaFaixa80 = mudancaFaixa80; }

    public BigDecimal getVendas120() { return vendas120; }
    public void setVendas120(BigDecimal vendas120) { this.vendas120 = vendas120; }

    public BigDecimal getComissaoBase120() { return comissaoBase120; }
    public void setComissaoBase120(BigDecimal comissaoBase120) { this.comissaoBase120 = comissaoBase120; }

    public BigDecimal getComissaoProposta120() { return comissaoProposta120; }
    public void setComissaoProposta120(BigDecimal comissaoProposta120) { this.comissaoProposta120 = comissaoProposta120; }

    public BigDecimal getDelta120() { return delta120; }
    public void setDelta120(BigDecimal delta120) { this.delta120 = delta120; }

    public BigDecimal getDeltaPerc120() { return deltaPerc120; }
    public void setDeltaPerc120(BigDecimal deltaPerc120) { this.deltaPerc120 = deltaPerc120; }

    public BigDecimal getTaxaMedia120() { return taxaMedia120; }
    public void setTaxaMedia120(BigDecimal taxaMedia120) { this.taxaMedia120 = taxaMedia120; }

    public BigDecimal getProjetada120() { return projetada120; }
    public void setProjetada120(BigDecimal projetada120) { this.projetada120 = projetada120; }

    public BigDecimal getDeltaProj120() { return deltaProj120; }
    public void setDeltaProj120(BigDecimal deltaProj120) { this.deltaProj120 = deltaProj120; }

    public int getMudancaFaixa120() { return mudancaFaixa120; }
    public void setMudancaFaixa120(int mudancaFaixa120) { this.mudancaFaixa120 = mudancaFaixa120; }

    public BigDecimal getVendas150() { return vendas150; }
    public void setVendas150(BigDecimal vendas150) { this.vendas150 = vendas150; }

    public BigDecimal getComissaoBase150() { return comissaoBase150; }
    public void setComissaoBase150(BigDecimal comissaoBase150) { this.comissaoBase150 = comissaoBase150; }

    public BigDecimal getComissaoProposta150() { return comissaoProposta150; }
    public void setComissaoProposta150(BigDecimal comissaoProposta150) { this.comissaoProposta150 = comissaoProposta150; }

    public BigDecimal getDelta150() { return delta150; }
    public void setDelta150(BigDecimal delta150) { this.delta150 = delta150; }

    public BigDecimal getDeltaPerc150() { return deltaPerc150; }
    public void setDeltaPerc150(BigDecimal deltaPerc150) { this.deltaPerc150 = deltaPerc150; }

    public BigDecimal getTaxaMedia150() { return taxaMedia150; }
    public void setTaxaMedia150(BigDecimal taxaMedia150) { this.taxaMedia150 = taxaMedia150; }

    public BigDecimal getProjetada150() { return projetada150; }
    public void setProjetada150(BigDecimal projetada150) { this.projetada150 = projetada150; }

    public BigDecimal getDeltaProj150() { return deltaProj150; }
    public void setDeltaProj150(BigDecimal deltaProj150) { this.deltaProj150 = deltaProj150; }

    public int getMudancaFaixa150() { return mudancaFaixa150; }
    public void setMudancaFaixa150(int mudancaFaixa150) { this.mudancaFaixa150 = mudancaFaixa150; }

    public StatusAceiteSimulacao getStatusAceite() { return statusAceite; }
    public void setStatusAceite(StatusAceiteSimulacao statusAceite) { this.statusAceite = statusAceite; }

    public OffsetDateTime getAceitoEm() { return aceitoEm; }
    public void setAceitoEm(OffsetDateTime aceitoEm) { this.aceitoEm = aceitoEm; }

    public String getAceitoPor() { return aceitoPor; }
    public void setAceitoPor(String aceitoPor) { this.aceitoPor = aceitoPor; }

    public String getMotivoAceite() { return motivoAceite; }
    public void setMotivoAceite(String motivoAceite) { this.motivoAceite = motivoAceite; }

    public String getHashProposta() { return hashProposta; }
    public void setHashProposta(String hashProposta) { this.hashProposta = hashProposta; }

    public OffsetDateTime getInvalidadoEm() { return invalidadoEm; }
    public void setInvalidadoEm(OffsetDateTime invalidadoEm) { this.invalidadoEm = invalidadoEm; }

    public String getMotivoInvalidacao() { return motivoInvalidacao; }
    public void setMotivoInvalidacao(String motivoInvalidacao) { this.motivoInvalidacao = motivoInvalidacao; }

    public List<SimulacaoVenda> getVendas() { return vendas; }
    public void setVendas(List<SimulacaoVenda> vendas) { this.vendas = vendas; }
}

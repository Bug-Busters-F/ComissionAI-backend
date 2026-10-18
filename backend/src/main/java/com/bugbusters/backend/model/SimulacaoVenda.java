package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_simulacao_venda")
public class SimulacaoVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulacao_id", nullable = false)
    private Simulacao simulacao;

    @Column(name = "id_venda", nullable = false)
    private UUID idVenda;

    @Column(length = 50)
    private String matricula;

    @Column(name = "cod_cargo")
    private Integer codCargo;

    @Column(name = "descri_cargo")
    private String descriCargo;

    @Column(name = "cod_loja")
    private Integer codLoja;

    @Column(name = "cod_marca")
    private Integer codMarca;

    @Column(name = "descr_marca")
    private String descrMarca;

    @Column(length = 50)
    private String canal;

    @Column(name = "data_venda", nullable = false)
    private LocalDate dataVenda;

    @Column(name = "valor_venda_original", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorVendaOriginal;

    @Column(name = "taxa_base_contratual", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaBaseContratual;

    @Column(name = "valor_comissao_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorComissaoBase;

    // --- Cenário 100% ---
    @Column(name = "regra_id_100")
    private Long regraId100;

    @Column(name = "regra_nome_100")
    private String regraNome100;

    @Column(name = "origem_taxa_100", nullable = false, length = 50)
    private String origemTaxa100;

    @Column(name = "atende_faixa_100", nullable = false)
    private boolean atendeFaixa100 = true;

    @Column(name = "taxa_aplicada_100", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaAplicada100;

    @Column(name = "valor_bonus_perc_100", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorBonusPerc100 = BigDecimal.ZERO;

    @Column(name = "valor_bonus_fixo_100", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorBonusFixo100 = BigDecimal.ZERO;

    @Column(name = "valor_comissao_100", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorComissao100;

    @Column(name = "delta_comissao_100", nullable = false, precision = 15, scale = 2)
    private BigDecimal deltaComissao100;

    // --- Cenário 80% ---
    @Column(name = "valor_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor80;

    @Column(name = "regra_id_80")
    private Long regraId80;

    @Column(name = "regra_nome_80")
    private String regraNome80;

    @Column(name = "origem_80", nullable = false, length = 50)
    private String origem80;

    @Column(name = "taxa_80", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxa80;

    @Column(name = "bonus_perc_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusPerc80 = BigDecimal.ZERO;

    @Column(name = "bonus_fixo_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusFixo80 = BigDecimal.ZERO;

    @Column(name = "comissao_80", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissao80;

    @Column(name = "mudou_faixa_80", nullable = false)
    private boolean mudouFaixa80;

    // --- Cenário 120% ---
    @Column(name = "valor_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor120;

    @Column(name = "regra_id_120")
    private Long regraId120;

    @Column(name = "regra_nome_120")
    private String regraNome120;

    @Column(name = "origem_120", nullable = false, length = 50)
    private String origem120;

    @Column(name = "taxa_120", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxa120;

    @Column(name = "bonus_perc_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusPerc120 = BigDecimal.ZERO;

    @Column(name = "bonus_fixo_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusFixo120 = BigDecimal.ZERO;

    @Column(name = "comissao_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissao120;

    @Column(name = "mudou_faixa_120", nullable = false)
    private boolean mudouFaixa120;

    // --- Cenário 150% ---
    @Column(name = "valor_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor150;

    @Column(name = "regra_id_150")
    private Long regraId150;

    @Column(name = "regra_nome_150")
    private String regraNome150;

    @Column(name = "origem_150", nullable = false, length = 50)
    private String origem150;

    @Column(name = "taxa_150", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxa150;

    @Column(name = "bonus_perc_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusPerc150 = BigDecimal.ZERO;

    @Column(name = "bonus_fixo_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusFixo150 = BigDecimal.ZERO;

    @Column(name = "comissao_150", nullable = false, precision = 15, scale = 2)
    private BigDecimal comissao150;

    @Column(name = "mudou_faixa_150", nullable = false)
    private boolean mudouFaixa150;

    @Column(nullable = false)
    private boolean valida = true;

    @Column(columnDefinition = "TEXT")
    private String impedimentos;

    @Column(name = "memoria_calculo_item", nullable = false, columnDefinition = "TEXT")
    private String memoriaCalculoItem;

    public SimulacaoVenda() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Simulacao getSimulacao() { return simulacao; }
    public void setSimulacao(Simulacao simulacao) { this.simulacao = simulacao; }

    public UUID getIdVenda() { return idVenda; }
    public void setIdVenda(UUID idVenda) { this.idVenda = idVenda; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public Integer getCodCargo() { return codCargo; }
    public void setCodCargo(Integer codCargo) { this.codCargo = codCargo; }

    public String getDescriCargo() { return descriCargo; }
    public void setDescriCargo(String descriCargo) { this.descriCargo = descriCargo; }

    public Integer getCodLoja() { return codLoja; }
    public void setCodLoja(Integer codLoja) { this.codLoja = codLoja; }

    public Integer getCodMarca() { return codMarca; }
    public void setCodMarca(Integer codMarca) { this.codMarca = codMarca; }

    public String getDescrMarca() { return descrMarca; }
    public void setDescrMarca(String descrMarca) { this.descrMarca = descrMarca; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public LocalDate getDataVenda() { return dataVenda; }
    public void setDataVenda(LocalDate dataVenda) { this.dataVenda = dataVenda; }

    public BigDecimal getValorVendaOriginal() { return valorVendaOriginal; }
    public void setValorVendaOriginal(BigDecimal valorVendaOriginal) { this.valorVendaOriginal = valorVendaOriginal; }

    public BigDecimal getTaxaBaseContratual() { return taxaBaseContratual; }
    public void setTaxaBaseContratual(BigDecimal taxaBaseContratual) { this.taxaBaseContratual = taxaBaseContratual; }

    public BigDecimal getValorComissaoBase() { return valorComissaoBase; }
    public void setValorComissaoBase(BigDecimal valorComissaoBase) { this.valorComissaoBase = valorComissaoBase; }

    public Long getRegraId100() { return regraId100; }
    public void setRegraId100(Long regraId100) { this.regraId100 = regraId100; }

    public String getRegraNome100() { return regraNome100; }
    public void setRegraNome100(String regraNome100) { this.regraNome100 = regraNome100; }

    public String getOrigemTaxa100() { return origemTaxa100; }
    public void setOrigemTaxa100(String origemTaxa100) { this.origemTaxa100 = origemTaxa100; }

    public boolean isAtendeFaixa100() { return atendeFaixa100; }
    public void setAtendeFaixa100(boolean atendeFaixa100) { this.atendeFaixa100 = atendeFaixa100; }

    public BigDecimal getTaxaAplicada100() { return taxaAplicada100; }
    public void setTaxaAplicada100(BigDecimal taxaAplicada100) { this.taxaAplicada100 = taxaAplicada100; }

    public BigDecimal getValorBonusPerc100() { return valorBonusPerc100; }
    public void setValorBonusPerc100(BigDecimal valorBonusPerc100) { this.valorBonusPerc100 = valorBonusPerc100; }

    public BigDecimal getValorBonusFixo100() { return valorBonusFixo100; }
    public void setValorBonusFixo100(BigDecimal valorBonusFixo100) { this.valorBonusFixo100 = valorBonusFixo100; }

    public BigDecimal getValorComissao100() { return valorComissao100; }
    public void setValorComissao100(BigDecimal valorComissao100) { this.valorComissao100 = valorComissao100; }

    public BigDecimal getDeltaComissao100() { return deltaComissao100; }
    public void setDeltaComissao100(BigDecimal deltaComissao100) { this.deltaComissao100 = deltaComissao100; }

    public BigDecimal getValor80() { return valor80; }
    public void setValor80(BigDecimal valor80) { this.valor80 = valor80; }

    public Long getRegraId80() { return regraId80; }
    public void setRegraId80(Long regraId80) { this.regraId80 = regraId80; }

    public String getRegraNome80() { return regraNome80; }
    public void setRegraNome80(String regraNome80) { this.regraNome80 = regraNome80; }

    public String getOrigem80() { return origem80; }
    public void setOrigem80(String origem80) { this.origem80 = origem80; }

    public BigDecimal getTaxa80() { return taxa80; }
    public void setTaxa80(BigDecimal taxa80) { this.taxa80 = taxa80; }

    public BigDecimal getBonusPerc80() { return bonusPerc80; }
    public void setBonusPerc80(BigDecimal bonusPerc80) { this.bonusPerc80 = bonusPerc80; }

    public BigDecimal getBonusFixo80() { return bonusFixo80; }
    public void setBonusFixo80(BigDecimal bonusFixo80) { this.bonusFixo80 = bonusFixo80; }

    public BigDecimal getComissao80() { return comissao80; }
    public void setComissao80(BigDecimal comissao80) { this.comissao80 = comissao80; }

    public boolean isMudouFaixa80() { return mudouFaixa80; }
    public void setMudouFaixa80(boolean mudouFaixa80) { this.mudouFaixa80 = mudouFaixa80; }

    public BigDecimal getValor120() { return valor120; }
    public void setValor120(BigDecimal valor120) { this.valor120 = valor120; }

    public Long getRegraId120() { return regraId120; }
    public void setRegraId120(Long regraId120) { this.regraId120 = regraId120; }

    public String getRegraNome120() { return regraNome120; }
    public void setRegraNome120(String regraNome120) { this.regraNome120 = regraNome120; }

    public String getOrigem120() { return origem120; }
    public void setOrigem120(String origem120) { this.origem120 = origem120; }

    public BigDecimal getTaxa120() { return taxa120; }
    public void setTaxa120(BigDecimal taxa120) { this.taxa120 = taxa120; }

    public BigDecimal getBonusPerc120() { return bonusPerc120; }
    public void setBonusPerc120(BigDecimal bonusPerc120) { this.bonusPerc120 = bonusPerc120; }

    public BigDecimal getBonusFixo120() { return bonusFixo120; }
    public void setBonusFixo120(BigDecimal bonusFixo120) { this.bonusFixo120 = bonusFixo120; }

    public BigDecimal getComissao120() { return comissao120; }
    public void setComissao120(BigDecimal comissao120) { this.comissao120 = comissao120; }

    public boolean isMudouFaixa120() { return mudouFaixa120; }
    public void setMudouFaixa120(boolean mudouFaixa120) { this.mudouFaixa120 = mudouFaixa120; }

    public BigDecimal getValor150() { return valor150; }
    public void setValor150(BigDecimal valor150) { this.valor150 = valor150; }

    public Long getRegraId150() { return regraId150; }
    public void setRegraId150(Long regraId150) { this.regraId150 = regraId150; }

    public String getRegraNome150() { return regraNome150; }
    public void setRegraNome150(String regraNome150) { this.regraNome150 = regraNome150; }

    public String getOrigem150() { return origem150; }
    public void setOrigem150(String origem150) { this.origem150 = origem150; }

    public BigDecimal getTaxa150() { return taxa150; }
    public void setTaxa150(BigDecimal taxa150) { this.taxa150 = taxa150; }

    public BigDecimal getBonusPerc150() { return bonusPerc150; }
    public void setBonusPerc150(BigDecimal bonusPerc150) { this.bonusPerc150 = bonusPerc150; }

    public BigDecimal getBonusFixo150() { return bonusFixo150; }
    public void setBonusFixo150(BigDecimal bonusFixo150) { this.bonusFixo150 = bonusFixo150; }

    public BigDecimal getComissao150() { return comissao150; }
    public void setComissao150(BigDecimal comissao150) { this.comissao150 = comissao150; }

    public boolean isMudouFaixa150() { return mudouFaixa150; }
    public void setMudouFaixa150(boolean mudouFaixa150) { this.mudouFaixa150 = mudouFaixa150; }

    public boolean isValida() { return valida; }
    public void setValida(boolean valida) { this.valida = valida; }

    public String getImpedimentos() { return impedimentos; }
    public void setImpedimentos(String impedimentos) { this.impedimentos = impedimentos; }

    public String getMemoriaCalculoItem() { return memoriaCalculoItem; }
    public void setMemoriaCalculoItem(String memoriaCalculoItem) { this.memoriaCalculoItem = memoriaCalculoItem; }
}

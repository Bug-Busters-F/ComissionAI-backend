package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_campanha")
public class Campanha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(name = "texto_original", nullable = false, columnDefinition = "TEXT")
    private String textoOriginal;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstadoCampanha estado = EstadoCampanha.DRAFT;

    @OneToMany(mappedBy = "campanha", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Regra> regras = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulacao_aceita_id")
    private Simulacao simulacaoAceita;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "versao_vigente_id")
    private CampanhaVersao versaoVigente;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "versao_proposta_id")
    private CampanhaVersao versaoProposta;

    @Column(precision = 18, scale = 2)
    private BigDecimal orcamento;

    @Column(name = "orcamento_extraido_ia", precision = 18, scale = 2)
    private BigDecimal orcamentoExtraidoIa;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_orcamento", length = 40)
    private OrigemOrcamentoCampanha origemOrcamento = OrigemOrcamentoCampanha.NAO_DEFINIDO;

    @Column(name = "orcamento_confirmado", nullable = false)
    private boolean orcamentoConfirmado = false;

    @Column(name = "orcamento_confirmado_por", length = 120)
    private String orcamentoConfirmadoPor;

    @Column(name = "orcamento_confirmado_em")
    private OffsetDateTime orcamentoConfirmadoEm;

    @Column(name = "justificativa_orcamento", columnDefinition = "TEXT")
    private String justificativaOrcamento;

    @Column(name = "encaminhada_gerente", nullable = false)
    private boolean encaminhadaGerente = false;

    @Column(name = "encaminhada_gerente_por", length = 120)
    private String encaminhadaGerentePor;

    @Column(name = "encaminhada_gerente_em")
    private OffsetDateTime encaminhadaGerenteEm;

    @Column(name = "observacao_encaminhamento", columnDefinition = "TEXT")
    private String observacaoEncaminhamento;

    @Version
    @Column(name = "versao_controle", nullable = false)
    private Long versaoControle = 0L;

    @Column(name = "removido_em")
    private OffsetDateTime removidoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    public Campanha() {}

    public Campanha(String titulo, String textoOriginal, LocalDate dataInicio, LocalDate dataFim) {
        this.titulo = titulo;
        this.textoOriginal = textoOriginal;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.estado = EstadoCampanha.DRAFT;
    }

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = OffsetDateTime.now();
        }
    }

    // Gestão da Coleção de Regras
    public List<Regra> getRegras() {
        return regras;
    }

    public void setRegras(List<Regra> regras) {
        this.regras.clear();
        if (regras != null) {
            for (Regra r : regras) {
                adicionarRegra(r);
            }
        }
    }

    public void adicionarRegra(Regra regra) {
        if (regra != null) {
            this.regras.add(regra);
            regra.setCampanha(this);
        }
    }

    public void removerRegra(Regra regra) {
        if (regra != null) {
            this.regras.remove(regra);
            regra.setCampanha(null);
        }
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getTextoOriginal() { return textoOriginal; }
    public void setTextoOriginal(String textoOriginal) { this.textoOriginal = textoOriginal; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }

    public EstadoCampanha getEstado() { return estado; }
    public void setEstado(EstadoCampanha estado) { this.estado = estado; }
    public void setEstado(String estado) {
        this.estado = estado != null ? EstadoCampanha.valueOf(estado) : EstadoCampanha.DRAFT;
    }

    public BigDecimal getOrcamento() { return orcamento; }
    public void setOrcamento(BigDecimal orcamento) { this.orcamento = orcamento; }

    public BigDecimal getOrcamentoExtraidoIa() { return orcamentoExtraidoIa; }
    public void setOrcamentoExtraidoIa(BigDecimal orcamentoExtraidoIa) { this.orcamentoExtraidoIa = orcamentoExtraidoIa; }

    public OrigemOrcamentoCampanha getOrigemOrcamento() { return origemOrcamento; }
    public void setOrigemOrcamento(OrigemOrcamentoCampanha origemOrcamento) { this.origemOrcamento = origemOrcamento; }

    public boolean isOrcamentoConfirmado() { return orcamentoConfirmado; }
    public void setOrcamentoConfirmado(boolean orcamentoConfirmado) { this.orcamentoConfirmado = orcamentoConfirmado; }

    public String getOrcamentoConfirmadoPor() { return orcamentoConfirmadoPor; }
    public void setOrcamentoConfirmadoPor(String orcamentoConfirmadoPor) { this.orcamentoConfirmadoPor = orcamentoConfirmadoPor; }

    public OffsetDateTime getOrcamentoConfirmadoEm() { return orcamentoConfirmadoEm; }
    public void setOrcamentoConfirmadoEm(OffsetDateTime orcamentoConfirmadoEm) { this.orcamentoConfirmadoEm = orcamentoConfirmadoEm; }

    public String getJustificativaOrcamento() { return justificativaOrcamento; }
    public void setJustificativaOrcamento(String justificativaOrcamento) { this.justificativaOrcamento = justificativaOrcamento; }

    public boolean isEncaminhadaGerente() { return encaminhadaGerente; }
    public void setEncaminhadaGerente(boolean encaminhadaGerente) { this.encaminhadaGerente = encaminhadaGerente; }

    public String getEncaminhadaGerentePor() { return encaminhadaGerentePor; }
    public void setEncaminhadaGerentePor(String encaminhadaGerentePor) { this.encaminhadaGerentePor = encaminhadaGerentePor; }

    public OffsetDateTime getEncaminhadaGerenteEm() { return encaminhadaGerenteEm; }
    public void setEncaminhadaGerenteEm(OffsetDateTime encaminhadaGerenteEm) { this.encaminhadaGerenteEm = encaminhadaGerenteEm; }

    public String getObservacaoEncaminhamento() { return observacaoEncaminhamento; }
    public void setObservacaoEncaminhamento(String observacaoEncaminhamento) { this.observacaoEncaminhamento = observacaoEncaminhamento; }

    public OffsetDateTime getRemovidoEm() { return removidoEm; }
    public void setRemovidoEm(OffsetDateTime removidoEm) { this.removidoEm = removidoEm; }
    
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }
    
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(OffsetDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    public Simulacao getSimulacaoAceita() { return simulacaoAceita; }
    public void setSimulacaoAceita(Simulacao simulacaoAceita) { this.simulacaoAceita = simulacaoAceita; }
    public CampanhaVersao getVersaoVigente() { return versaoVigente; }
    public void setVersaoVigente(CampanhaVersao versaoVigente) { this.versaoVigente = versaoVigente; }
    public CampanhaVersao getVersaoProposta() { return versaoProposta; }
    public void setVersaoProposta(CampanhaVersao versaoProposta) { this.versaoProposta = versaoProposta; }
    public Long getVersaoControle() { return versaoControle; }

    public CampanhaVersao versaoParaRevisao() {
        return versaoProposta != null ? versaoProposta : versaoVigente;
    }

    public String tituloParaRevisao() {
        return versaoParaRevisao() != null ? versaoParaRevisao().getTitulo() : titulo;
    }

    public String textoParaRevisao() {
        return versaoParaRevisao() != null ? versaoParaRevisao().getTextoOriginal() : textoOriginal;
    }

    public LocalDate inicioParaRevisao() {
        return versaoParaRevisao() != null ? versaoParaRevisao().getDataInicio() : dataInicio;
    }

    public LocalDate fimParaRevisao() {
        return versaoParaRevisao() != null ? versaoParaRevisao().getDataFim() : dataFim;
    }

    public BigDecimal orcamentoParaRevisao() {
        return versaoParaRevisao() != null && versaoParaRevisao().getOrcamento() != null
                ? versaoParaRevisao().getOrcamento()
                : orcamento;
    }

    public boolean orcamentoConfirmadoParaRevisao() {
        return versaoParaRevisao() != null
                ? versaoParaRevisao().isOrcamentoConfirmado()
                : orcamentoConfirmado;
    }

    public OrigemOrcamentoCampanha origemOrcamentoParaRevisao() {
        return versaoParaRevisao() != null && versaoParaRevisao().getOrigemOrcamento() != null
                ? versaoParaRevisao().getOrigemOrcamento()
                : (origemOrcamento != null ? origemOrcamento : OrigemOrcamentoCampanha.NAO_DEFINIDO);
    }
}

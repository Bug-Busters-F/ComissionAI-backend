package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tb_campanha_versao", uniqueConstraints = @UniqueConstraint(name = "uk_campanha_versao_numero", columnNames = {"campanha_id", "numero"}))
public class CampanhaVersao {
    @Column(name = "ultima_etapa", nullable = false) private Integer ultimaEtapa = 1;
    @Column(name = "etapa_mais_avancada", nullable = false) private Integer etapaMaisAvancada = 1;
    public Integer getUltimaEtapa() { return ultimaEtapa; }
    public void setUltimaEtapa(Integer value) { ultimaEtapa = value; }
    public Integer getEtapaMaisAvancada() { return etapaMaisAvancada; }
    public void setEtapaMaisAvancada(Integer value) { etapaMaisAvancada = value; }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "campanha_id", nullable = false) private Campanha campanha;
    @Column(nullable = false) private Integer numero;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StatusVersaoCampanha status;
    @Column(nullable = false) private String titulo;
    @Column(name = "texto_original", nullable = false, columnDefinition = "TEXT") private String textoOriginal;
    @Column(name = "data_inicio", nullable = false) private LocalDate dataInicio;
    @Column(name = "data_fim", nullable = false) private LocalDate dataFim;
    @Column(name = "criado_em", nullable = false, updatable = false) private OffsetDateTime criadoEm = OffsetDateTime.now();
    @Column(name = "publicado_em") private OffsetDateTime publicadoEm;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "versao_anterior_id") private CampanhaVersao versaoAnterior;
    @Column(name = "publicado_por", length = 150) private String publicadoPor;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "simulacao_aceita_id") private Simulacao simulacaoAceita;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "programa_python_aprovado_id") private CampanhaProgramaPython programaPythonAprovado;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_esteira", nullable = false, length = 30)
    private EstadoEsteiraCampanha estadoEsteira = EstadoEsteiraCampanha.EM_ELABORACAO;

    @Column(name = "devolvido_em")
    private OffsetDateTime devolvidoEm;

    @Column(name = "devolvido_por", length = 150)
    private String devolvidoPor;

    @Column(name = "motivo_devolucao", columnDefinition = "TEXT")
    private String motivoDevolucao;

    @Column(name = "etapa_devolucao")
    private Integer etapaDevolucao;

    @Column(name = "gerente_aprovador", length = 150)
    private String gerenteAprovador;

    @Column(name = "gerente_aprovado_em")
    private OffsetDateTime gerenteAprovadoEm;

    @Column(name = "parecer_gerente", columnDefinition = "TEXT")
    private String parecerGerente;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status_aprovacao_codigo", nullable = false, length = 30)
    private StatusAprovacaoCodigo statusAprovacaoCodigo = StatusAprovacaoCodigo.PENDENTE;

    @Column(name = "hash_artefatos_codigo", length = 64)
    private String hashArtefatosCodigo;

    @Column(name = "codigo_aprovado_em")
    private OffsetDateTime codigoAprovadoEm;

    @Column(name = "codigo_aprovado_por", length = 150)
    private String codigoAprovadoPor;

    @Column(name = "codigo_invalidado_em")
    private OffsetDateTime codigoInvalidadoEm;

    @Column(name = "motivo_invalidacao_codigo", columnDefinition = "TEXT")
    private String motivoInvalidacaoCodigo;

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Campanha getCampanha(){return campanha;} public void setCampanha(Campanha c){this.campanha=c;}
    public Integer getNumero(){return numero;} public void setNumero(Integer n){this.numero=n;}
    public StatusVersaoCampanha getStatus(){return status;} public void setStatus(StatusVersaoCampanha s){this.status=s;}
    public String getTitulo(){return titulo;} public void setTitulo(String v){this.titulo=v;}
    public String getTextoOriginal(){return textoOriginal;} public void setTextoOriginal(String v){this.textoOriginal=v;}
    public LocalDate getDataInicio(){return dataInicio;} public void setDataInicio(LocalDate v){this.dataInicio=v;}
    public LocalDate getDataFim(){return dataFim;} public void setDataFim(LocalDate v){this.dataFim=v;}
    public OffsetDateTime getCriadoEm(){return criadoEm;} public void setCriadoEm(OffsetDateTime v){this.criadoEm=v;}
    public OffsetDateTime getPublicadoEm(){return publicadoEm;} public void setPublicadoEm(OffsetDateTime v){this.publicadoEm=v;}
    public CampanhaVersao getVersaoAnterior(){return versaoAnterior;} public void setVersaoAnterior(CampanhaVersao v){this.versaoAnterior=v;}
    public String getPublicadoPor(){return publicadoPor;} public void setPublicadoPor(String v){this.publicadoPor=v;}
    public Simulacao getSimulacaoAceita(){return simulacaoAceita;} public void setSimulacaoAceita(Simulacao v){this.simulacaoAceita=v;}
    public CampanhaProgramaPython getProgramaPythonAprovado(){return programaPythonAprovado;} public void setProgramaPythonAprovado(CampanhaProgramaPython v){this.programaPythonAprovado=v;}

    public EstadoEsteiraCampanha getEstadoEsteira(){return estadoEsteira;} public void setEstadoEsteira(EstadoEsteiraCampanha e){this.estadoEsteira=e;}
    public OffsetDateTime getDevolvidoEm(){return devolvidoEm;} public void setDevolvidoEm(OffsetDateTime d){this.devolvidoEm=d;}
    public String getDevolvidoPor(){return devolvidoPor;} public void setDevolvidoPor(String p){this.devolvidoPor=p;}
    public String getMotivoDevolucao(){return motivoDevolucao;} public void setMotivoDevolucao(String m){this.motivoDevolucao=m;}
    public Integer getEtapaDevolucao(){return etapaDevolucao;} public void setEtapaDevolucao(Integer ed){this.etapaDevolucao=ed;}
    public String getGerenteAprovador(){return gerenteAprovador;} public void setGerenteAprovador(String ga){this.gerenteAprovador=ga;}
    public OffsetDateTime getGerenteAprovadoEm(){return gerenteAprovadoEm;} public void setGerenteAprovadoEm(OffsetDateTime gae){this.gerenteAprovadoEm=gae;}
    public String getParecerGerente(){return parecerGerente;} public void setParecerGerente(String pg){this.parecerGerente=pg;}
    public boolean isDevolvida(){return EstadoEsteiraCampanha.DEVOLVIDA.equals(this.estadoEsteira);}

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

    public StatusAprovacaoCodigo getStatusAprovacaoCodigo() { return statusAprovacaoCodigo; }
    public void setStatusAprovacaoCodigo(StatusAprovacaoCodigo statusAprovacaoCodigo) { this.statusAprovacaoCodigo = statusAprovacaoCodigo; }
    public String getHashArtefatosCodigo() { return hashArtefatosCodigo; }
    public void setHashArtefatosCodigo(String hashArtefatosCodigo) { this.hashArtefatosCodigo = hashArtefatosCodigo; }
    public OffsetDateTime getCodigoAprovadoEm() { return codigoAprovadoEm; }
    public void setCodigoAprovadoEm(OffsetDateTime codigoAprovadoEm) { this.codigoAprovadoEm = codigoAprovadoEm; }
    public String getCodigoAprovadoPor() { return codigoAprovadoPor; }
    public void setCodigoAprovadoPor(String codigoAprovadoPor) { this.codigoAprovadoPor = codigoAprovadoPor; }
    public OffsetDateTime getCodigoInvalidadoEm() { return codigoInvalidadoEm; }
    public void setCodigoInvalidadoEm(OffsetDateTime codigoInvalidadoEm) { this.codigoInvalidadoEm = codigoInvalidadoEm; }
    public String getMotivoInvalidacaoCodigo() { return motivoInvalidacaoCodigo; }
    public void setMotivoInvalidacaoCodigo(String motivoInvalidacaoCodigo) { this.motivoInvalidacaoCodigo = motivoInvalidacaoCodigo; }
}

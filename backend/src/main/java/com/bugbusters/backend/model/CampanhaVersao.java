package com.bugbusters.backend.model;

import jakarta.persistence.*;
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
}

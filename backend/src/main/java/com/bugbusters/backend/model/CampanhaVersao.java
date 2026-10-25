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
}

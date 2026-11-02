package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_linha_preparacao",
       uniqueConstraints = @UniqueConstraint(name = "uk_linha_prep_lote_numero", columnNames = {"lote_preparacao_id", "numero_linha"}))
public class LinhaPreparacao {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_preparacao_id", nullable = false)
    private LotePreparacao lotePreparacao;

    @Column(name = "numero_linha", nullable = false)
    private int numeroLinha;

    @Version
    @Column(nullable = false)
    private Long versao = 0L;

    @Column(name = "status_linha", nullable = false, length = 30)
    private String statusLinha = "VALIDA";

    @Column(name = "severidade_maxima", length = 20)
    private String severidadeMaxima;

    @Column(name = "origem_ultima_edicao", nullable = false, length = 30)
    private String origemUltimaEdicao = "ORIGINAL";

    @Column(nullable = false)
    private boolean editada = false;

    @Column(name = "ignorar_na_efetivacao", nullable = false)
    private boolean ignorarNaEfetivacao = false;

    @Column(name = "registro_alvo_id")
    private UUID registroAlvoId;

    @Column(name = "conteudo_original_json", nullable = false, columnDefinition = "TEXT")
    private String conteudoOriginalJson;

    @Column(name = "data_referencia")
    private LocalDate dataReferencia;

    @Column(name = "cod_marca")
    private Integer codMarca;

    @Column(name = "descr_marca", length = 150)
    private String descrMarca;

    @Column(name = "cod_loja")
    private Integer codLoja;

    @Column(name = "descr_loja", length = 150)
    private String descrLoja;

    @Column(name = "matricula", length = 100)
    private String matricula;

    @Column(name = "data_admissao")
    private LocalDate dataAdmissao;

    @Column(name = "data_demissao")
    private LocalDate dataDemissao;

    @Column(name = "cod_cargo")
    private Integer codCargo;

    @Column(name = "descr_cargo", length = 150)
    private String descrCargo;

    @Column(name = "valor_venda", precision = 15, scale = 2)
    private BigDecimal valorVenda;

    @Column(name = "erros_validacao_json", nullable = false, columnDefinition = "TEXT")
    private String errosValidacaoJson = "[]";

    @Column(name = "campos_com_erro", length = 255)
    private String camposComErro;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    @Column(name = "atualizado_por", length = 150)
    private String atualizadoPor;

    public LinhaPreparacao() {}

    public LinhaPreparacao(LotePreparacao lotePreparacao, int numeroLinha, String conteudoOriginalJson) {
        this.id = UUID.randomUUID();
        this.lotePreparacao = lotePreparacao;
        this.numeroLinha = numeroLinha;
        this.conteudoOriginalJson = conteudoOriginalJson != null ? conteudoOriginalJson : "{}";
        this.statusLinha = "VALIDA";
        this.origemUltimaEdicao = "ORIGINAL";
        this.versao = 0L;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.versao == null) {
            this.versao = 0L;
        }
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LotePreparacao getLotePreparacao() { return lotePreparacao; }
    public void setLotePreparacao(LotePreparacao lotePreparacao) { this.lotePreparacao = lotePreparacao; }

    public int getNumeroLinha() { return numeroLinha; }
    public void setNumeroLinha(int numeroLinha) { this.numeroLinha = numeroLinha; }

    public Long getVersao() { return versao; }
    public void setVersao(Long versao) { this.versao = versao; }

    public String getStatusLinha() { return statusLinha; }
    public void setStatusLinha(String statusLinha) { this.statusLinha = statusLinha; }

    public String getSeveridadeMaxima() { return severidadeMaxima; }
    public void setSeveridadeMaxima(String severidadeMaxima) { this.severidadeMaxima = severidadeMaxima; }

    public String getOrigemUltimaEdicao() { return origemUltimaEdicao; }
    public void setOrigemUltimaEdicao(String origemUltimaEdicao) { this.origemUltimaEdicao = origemUltimaEdicao; }

    public boolean isEditada() { return editada; }
    public void setEditada(boolean editada) { this.editada = editada; }

    public boolean isIgnorarNaEfetivacao() { return ignorarNaEfetivacao; }
    public void setIgnorarNaEfetivacao(boolean ignorarNaEfetivacao) { this.ignorarNaEfetivacao = ignorarNaEfetivacao; }

    public UUID getRegistroAlvoId() { return registroAlvoId; }
    public void setRegistroAlvoId(UUID registroAlvoId) { this.registroAlvoId = registroAlvoId; }

    public String getConteudoOriginalJson() { return conteudoOriginalJson; }
    public void setConteudoOriginalJson(String conteudoOriginalJson) { this.conteudoOriginalJson = conteudoOriginalJson; }

    public LocalDate getDataReferencia() { return dataReferencia; }
    public void setDataReferencia(LocalDate dataReferencia) { this.dataReferencia = dataReferencia; }

    public Integer getCodMarca() { return codMarca; }
    public void setCodMarca(Integer codMarca) { this.codMarca = codMarca; }

    public String getDescrMarca() { return descrMarca; }
    public void setDescrMarca(String descrMarca) { this.descrMarca = descrMarca; }

    public Integer getCodLoja() { return codLoja; }
    public void setCodLoja(Integer codLoja) { this.codLoja = codLoja; }

    public String getDescrLoja() { return descrLoja; }
    public void setDescrLoja(String descrLoja) { this.descrLoja = descrLoja; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public LocalDate getDataAdmissao() { return dataAdmissao; }
    public void setDataAdmissao(LocalDate dataAdmissao) { this.dataAdmissao = dataAdmissao; }

    public LocalDate getDataDemissao() { return dataDemissao; }
    public void setDataDemissao(LocalDate dataDemissao) { this.dataDemissao = dataDemissao; }

    public Integer getCodCargo() { return codCargo; }
    public void setCodCargo(Integer codCargo) { this.codCargo = codCargo; }

    public String getDescrCargo() { return descrCargo; }
    public void setDescrCargo(String descrCargo) { this.descrCargo = descrCargo; }

    public BigDecimal getValorVenda() { return valorVenda; }
    public void setValorVenda(BigDecimal valorVenda) { this.valorVenda = valorVenda; }

    public String getErrosValidacaoJson() { return errosValidacaoJson; }
    public void setErrosValidacaoJson(String errosValidacaoJson) { this.errosValidacaoJson = errosValidacaoJson; }

    public String getCamposComErro() { return camposComErro; }
    public void setCamposComErro(String camposComErro) { this.camposComErro = camposComErro; }

    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(OffsetDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    public String getAtualizadoPor() { return atualizadoPor; }
    public void setAtualizadoPor(String atualizadoPor) { this.atualizadoPor = atualizadoPor; }
}

package com.bugbusters.backend.model;

import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Registro rastreável de aceite formal de pendências e riscos (BUG-102 / BACK-7).
 * Permite formalizar e auditar a mitigação de inconsistências cadastrais ou de regras
 * antes da simulação e da homologação na esteira.
 */
@Entity
@Table(name = "tb_campanha_pendencia_aceite")
public class CampanhaPendenciaAceite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_id", nullable = false)
    private Campanha campanha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_versao_id")
    private CampanhaVersao campanhaVersao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regra_id")
    private Regra regra;

    @Column(name = "pendencia_identificador", length = 150, nullable = false)
    private String pendenciaIdentificador;

    @Column(name = "codigo", length = 100, nullable = false)
    private String codigo;

    @Column(name = "campo", length = 100)
    private String campo;

    @Column(name = "mensagem", columnDefinition = "TEXT")
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidade", length = 50, nullable = false)
    private SeveridadePendencia severidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private StatusAceitePendencia status = StatusAceitePendencia.ACEITA;

    @Column(name = "aceito_por", length = 150, nullable = false)
    private String aceitoPor;

    @Column(name = "aceito_em", nullable = false)
    private OffsetDateTime aceitoEm = OffsetDateTime.now();

    @Column(name = "motivo_aceite", columnDefinition = "TEXT", nullable = false)
    private String motivoAceite;

    @Column(name = "revogado_por", length = 150)
    private String revogadoPor;

    @Column(name = "revogado_em")
    private OffsetDateTime revogadoEm;

    @Column(name = "motivo_revogacao", columnDefinition = "TEXT")
    private String motivoRevogacao;

    public CampanhaPendenciaAceite() {
    }

    public CampanhaPendenciaAceite(
            Campanha campanha,
            CampanhaVersao campanhaVersao,
            Regra regra,
            String pendenciaIdentificador,
            String codigo,
            String campo,
            String mensagem,
            SeveridadePendencia severidade,
            String aceitoPor,
            String motivoAceite
    ) {
        this.campanha = campanha;
        this.campanhaVersao = campanhaVersao;
        this.regra = regra;
        this.pendenciaIdentificador = pendenciaIdentificador;
        this.codigo = codigo;
        this.campo = campo;
        this.mensagem = mensagem;
        this.severidade = severidade != null ? severidade : SeveridadePendencia.IMPEDIMENTO;
        this.status = StatusAceitePendencia.ACEITA;
        this.aceitoPor = aceitoPor;
        this.aceitoEm = OffsetDateTime.now();
        this.motivoAceite = motivoAceite;
    }

    public boolean isAtiva() {
        return StatusAceitePendencia.ACEITA.equals(this.status);
    }

    public void revogar(String responsavel, String motivo) {
        this.status = StatusAceitePendencia.REVOGADA;
        this.revogadoPor = responsavel;
        this.revogadoEm = OffsetDateTime.now();
        this.motivoRevogacao = motivo;
    }

    // Getters e Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public Regra getRegra() {
        return regra;
    }

    public void setRegra(Regra regra) {
        this.regra = regra;
    }

    public String getPendenciaIdentificador() {
        return pendenciaIdentificador;
    }

    public void setPendenciaIdentificador(String pendenciaIdentificador) {
        this.pendenciaIdentificador = pendenciaIdentificador;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public SeveridadePendencia getSeveridade() {
        return severidade;
    }

    public void setSeveridade(SeveridadePendencia severidade) {
        this.severidade = severidade;
    }

    public StatusAceitePendencia getStatus() {
        return status;
    }

    public void setStatus(StatusAceitePendencia status) {
        this.status = status;
    }

    public String getAceitoPor() {
        return aceitoPor;
    }

    public void setAceitoPor(String aceitoPor) {
        this.aceitoPor = aceitoPor;
    }

    public OffsetDateTime getAceitoEm() {
        return aceitoEm;
    }

    public void setAceitoEm(OffsetDateTime aceitoEm) {
        this.aceitoEm = aceitoEm;
    }

    public String getMotivoAceite() {
        return motivoAceite;
    }

    public void setMotivoAceite(String motivoAceite) {
        this.motivoAceite = motivoAceite;
    }

    public String getRevogadoPor() {
        return revogadoPor;
    }

    public void setRevogadoPor(String revogadoPor) {
        this.revogadoPor = revogadoPor;
    }

    public OffsetDateTime getRevogadoEm() {
        return revogadoEm;
    }

    public void setRevogadoEm(OffsetDateTime revogadoEm) {
        this.revogadoEm = revogadoEm;
    }

    public String getMotivoRevogacao() {
        return motivoRevogacao;
    }

    public void setMotivoRevogacao(String motivoRevogacao) {
        this.motivoRevogacao = motivoRevogacao;
    }
}


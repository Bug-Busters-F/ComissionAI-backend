package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de registro imutável de auditoria geral das ações relevantes (BUG-111 / BACK-16).
 */
@Entity
@Table(name = "tb_auditoria_geral")
public class AuditoriaGeral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID protocolo;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora = OffsetDateTime.now();

    @Column(nullable = false, length = 150)
    private String usuario;

    @Column(name = "perfil_usuario", length = 50)
    private String perfilUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_acao", nullable = false, length = 60)
    private TipoAcaoAuditoria tipoAcao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entidade", nullable = false, length = 60)
    private TipoEntidadeAuditoria tipoEntidade;

    @Column(name = "entidade_id", length = 100)
    private String entidadeId;

    @Column(name = "versao_entidade")
    private Integer versaoEntidade;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "dados_anteriores", columnDefinition = "TEXT")
    private String dadosAnteriores;

    @Column(name = "dados_novos", columnDefinition = "TEXT")
    private String dadosNovos;

    @Column(name = "ip_origem", length = 60)
    private String ipOrigem;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(nullable = false)
    private boolean sucesso = true;

    @Column(name = "detalhe_erro", columnDefinition = "TEXT")
    private String detalheErro;

    public AuditoriaGeral() {
        this.protocolo = UUID.randomUUID();
        this.dataHora = OffsetDateTime.now();
    }

    public AuditoriaGeral(
            String usuario,
            String perfilUsuario,
            TipoAcaoAuditoria tipoAcao,
            TipoEntidadeAuditoria tipoEntidade,
            String entidadeId,
            Integer versaoEntidade,
            String descricao,
            String dadosAnteriores,
            String dadosNovos,
            String ipOrigem,
            String userAgent,
            boolean sucesso,
            String detalheErro
    ) {
        this();
        this.usuario = usuario != null && !usuario.isBlank() ? usuario : "SISTEMA";
        this.perfilUsuario = perfilUsuario;
        this.tipoAcao = tipoAcao;
        this.tipoEntidade = tipoEntidade;
        this.entidadeId = entidadeId;
        this.versaoEntidade = versaoEntidade;
        this.descricao = descricao;
        this.dadosAnteriores = dadosAnteriores;
        this.dadosNovos = dadosNovos;
        this.ipOrigem = ipOrigem;
        this.userAgent = userAgent;
        this.sucesso = sucesso;
        this.detalheErro = detalheErro;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(UUID protocolo) {
        this.protocolo = protocolo;
    }

    public OffsetDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(OffsetDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPerfilUsuario() {
        return perfilUsuario;
    }

    public void setPerfilUsuario(String perfilUsuario) {
        this.perfilUsuario = perfilUsuario;
    }

    public TipoAcaoAuditoria getTipoAcao() {
        return tipoAcao;
    }

    public void setTipoAcao(TipoAcaoAuditoria tipoAcao) {
        this.tipoAcao = tipoAcao;
    }

    public TipoEntidadeAuditoria getTipoEntidade() {
        return tipoEntidade;
    }

    public void setTipoEntidade(TipoEntidadeAuditoria tipoEntidade) {
        this.tipoEntidade = tipoEntidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public void setEntidadeId(String entidadeId) {
        this.entidadeId = entidadeId;
    }

    public Integer getVersaoEntidade() {
        return versaoEntidade;
    }

    public void setVersaoEntidade(Integer versaoEntidade) {
        this.versaoEntidade = versaoEntidade;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getDadosAnteriores() {
        return dadosAnteriores;
    }

    public void setDadosAnteriores(String dadosAnteriores) {
        this.dadosAnteriores = dadosAnteriores;
    }

    public String getDadosNovos() {
        return dadosNovos;
    }

    public void setDadosNovos(String dadosNovos) {
        this.dadosNovos = dadosNovos;
    }

    public String getIpOrigem() {
        return ipOrigem;
    }

    public void setIpOrigem(String ipOrigem) {
        this.ipOrigem = ipOrigem;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public void setSucesso(boolean sucesso) {
        this.sucesso = sucesso;
    }

    public String getDetalheErro() {
        return detalheErro;
    }

    public void setDetalheErro(String detalheErro) {
        this.detalheErro = detalheErro;
    }
}


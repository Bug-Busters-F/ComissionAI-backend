package com.bugbusters.backend.dto.auditoria;

import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import java.util.UUID;

/**
 * Contexto estruturado para registro de eventos auditáveis com vínculos relacionais explícitos.
 */
public final class AuditoriaEventoContexto {
    private String usuario;
    private String perfilUsuario;
    private TipoAcaoAuditoria tipoAcao;
    private TipoEntidadeAuditoria tipoEntidade;
    private String entidadeId;
    private Integer versaoEntidade;
    private String descricao;
    private String dadosAnteriores;
    private String dadosNovos;
    private String ipOrigem;
    private String userAgent;
    private Boolean sucesso = true;
    private String detalheErro;

    private Long campanhaId;
    private Long campanhaVersaoId;
    private String campanhaTitulo;
    private Long envioArquivoId;
    private UUID arquivoImportacaoId;
    private UUID lotePreparacaoId;
    private String idLote;
    private Long reportId;
    private String nomeArquivo;
    private UUID simulacaoId;
    private UUID apuracaoCampanhaId;
    private UUID execucaoCalculoId;
    private UUID fechamentoId;
    private UUID protocoloCalculo;
    private UUID logCalculoId;
    private String competencia;

    private AuditoriaEventoContexto() {}

    public static AuditoriaEventoContexto builder() {
        return new AuditoriaEventoContexto();
    }

    public AuditoriaEventoContexto usuario(String usuario) { this.usuario = usuario; return this; }
    public AuditoriaEventoContexto perfilUsuario(String perfilUsuario) { this.perfilUsuario = perfilUsuario; return this; }
    public AuditoriaEventoContexto tipoAcao(TipoAcaoAuditoria tipoAcao) { this.tipoAcao = tipoAcao; return this; }
    public AuditoriaEventoContexto tipoEntidade(TipoEntidadeAuditoria tipoEntidade) { this.tipoEntidade = tipoEntidade; return this; }
    public AuditoriaEventoContexto entidadeId(String entidadeId) { this.entidadeId = entidadeId; return this; }
    public AuditoriaEventoContexto versaoEntidade(Integer versaoEntidade) { this.versaoEntidade = versaoEntidade; return this; }
    public AuditoriaEventoContexto descricao(String descricao) { this.descricao = descricao; return this; }
    public AuditoriaEventoContexto dadosAnteriores(String dadosAnteriores) { this.dadosAnteriores = dadosAnteriores; return this; }
    public AuditoriaEventoContexto dadosNovos(String dadosNovos) { this.dadosNovos = dadosNovos; return this; }
    public AuditoriaEventoContexto ipOrigem(String ipOrigem) { this.ipOrigem = ipOrigem; return this; }
    public AuditoriaEventoContexto userAgent(String userAgent) { this.userAgent = userAgent; return this; }
    public AuditoriaEventoContexto sucesso(Boolean sucesso) { this.sucesso = sucesso; return this; }
    public AuditoriaEventoContexto detalheErro(String detalheErro) { this.detalheErro = detalheErro; return this; }
    public AuditoriaEventoContexto campanhaId(Long campanhaId) { this.campanhaId = campanhaId; return this; }
    public AuditoriaEventoContexto campanhaVersaoId(Long campanhaVersaoId) { this.campanhaVersaoId = campanhaVersaoId; return this; }
    public AuditoriaEventoContexto campanhaTitulo(String campanhaTitulo) { this.campanhaTitulo = campanhaTitulo; return this; }
    public AuditoriaEventoContexto envioArquivoId(Long envioArquivoId) { this.envioArquivoId = envioArquivoId; return this; }
    public AuditoriaEventoContexto arquivoImportacaoId(UUID arquivoImportacaoId) { this.arquivoImportacaoId = arquivoImportacaoId; return this; }
    public AuditoriaEventoContexto lotePreparacaoId(UUID lotePreparacaoId) { this.lotePreparacaoId = lotePreparacaoId; return this; }
    public AuditoriaEventoContexto idLote(String idLote) { this.idLote = idLote; return this; }
    public AuditoriaEventoContexto reportId(Long reportId) { this.reportId = reportId; return this; }
    public AuditoriaEventoContexto nomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; return this; }
    public AuditoriaEventoContexto simulacaoId(UUID simulacaoId) { this.simulacaoId = simulacaoId; return this; }
    public AuditoriaEventoContexto apuracaoCampanhaId(UUID apuracaoCampanhaId) { this.apuracaoCampanhaId = apuracaoCampanhaId; return this; }
    public AuditoriaEventoContexto execucaoCalculoId(UUID execucaoCalculoId) { this.execucaoCalculoId = execucaoCalculoId; return this; }
    public AuditoriaEventoContexto fechamentoId(UUID fechamentoId) { this.fechamentoId = fechamentoId; return this; }
    public AuditoriaEventoContexto protocoloCalculo(UUID protocoloCalculo) { this.protocoloCalculo = protocoloCalculo; return this; }
    public AuditoriaEventoContexto logCalculoId(UUID logCalculoId) { this.logCalculoId = logCalculoId; return this; }
    public AuditoriaEventoContexto competencia(String competencia) { this.competencia = competencia; return this; }

    public String getUsuario() { return usuario; }
    public String getPerfilUsuario() { return perfilUsuario; }
    public TipoAcaoAuditoria getTipoAcao() { return tipoAcao; }
    public TipoEntidadeAuditoria getTipoEntidade() { return tipoEntidade; }
    public String getEntidadeId() { return entidadeId; }
    public Integer getVersaoEntidade() { return versaoEntidade; }
    public String getDescricao() { return descricao; }
    public String getDadosAnteriores() { return dadosAnteriores; }
    public String getDadosNovos() { return dadosNovos; }
    public String getIpOrigem() { return ipOrigem; }
    public String getUserAgent() { return userAgent; }
    public Boolean getSucesso() { return sucesso; }
    public String getDetalheErro() { return detalheErro; }
    public Long getCampanhaId() { return campanhaId; }
    public Long getCampanhaVersaoId() { return campanhaVersaoId; }
    public String getCampanhaTitulo() { return campanhaTitulo; }
    public Long getEnvioArquivoId() { return envioArquivoId; }
    public UUID getArquivoImportacaoId() { return arquivoImportacaoId; }
    public UUID getLotePreparacaoId() { return lotePreparacaoId; }
    public String getIdLote() { return idLote; }
    public Long getReportId() { return reportId; }
    public String getNomeArquivo() { return nomeArquivo; }
    public UUID getSimulacaoId() { return simulacaoId; }
    public UUID getApuracaoCampanhaId() { return apuracaoCampanhaId; }
    public UUID getExecucaoCalculoId() { return execucaoCalculoId; }
    public UUID getFechamentoId() { return fechamentoId; }
    public UUID getProtocoloCalculo() { return protocoloCalculo; }
    public UUID getLogCalculoId() { return logCalculoId; }
    public String getCompetencia() { return competencia; }
}

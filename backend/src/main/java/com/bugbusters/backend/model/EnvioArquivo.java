package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_envio_arquivo")
public class EnvioArquivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_lote", length = 100, unique = true)
    private String idLote;

    @Column(name = "report_id", unique = true)
    private Long reportId;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(name = "tipo_base", nullable = false, length = 50)
    private String tipoBase;

    @Column(name = "hash_conteudo", length = 64)
    private String hashConteudo;

    @Column(name = "competencia", length = 7)
    private String competencia;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "mensagem", columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "total_linhas", nullable = false)
    private int totalLinhas;

    @Column(name = "linhas_validas", nullable = false)
    private int linhasValidas;

    @Column(name = "rejeicao_integral", nullable = false)
    private boolean rejeicaoIntegral;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "iniciado_em")
    private OffsetDateTime iniciadoEm;

    @Column(name = "finalizado_em")
    private OffsetDateTime finalizadoEm;

    @Column(name = "excluido_em")
    private OffsetDateTime excluidoEm;

    @Column(name = "excluido_por", length = 100)
    private String excluidoPor;

    @OneToMany(mappedBy = "envio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InconsistenciaImportacao> inconsistencias = new ArrayList<>();

    public EnvioArquivo() {}

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdLote() { return idLote; }
    public void setIdLote(String idLote) { this.idLote = idLote; }

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }

    public String getTipoBase() { return tipoBase; }
    public void setTipoBase(String tipoBase) { this.tipoBase = tipoBase; }

    public String getHashConteudo() { return hashConteudo; }
    public void setHashConteudo(String hashConteudo) { this.hashConteudo = hashConteudo; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public int getTotalLinhas() { return totalLinhas; }
    public void setTotalLinhas(int totalLinhas) { this.totalLinhas = totalLinhas; }

    public int getLinhasValidas() { return linhasValidas; }
    public void setLinhasValidas(int linhasValidas) { this.linhasValidas = linhasValidas; }

    public boolean isRejeicaoIntegral() { return rejeicaoIntegral; }
    public void setRejeicaoIntegral(boolean rejeicaoIntegral) { this.rejeicaoIntegral = rejeicaoIntegral; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }

    public OffsetDateTime getIniciadoEm() { return iniciadoEm; }
    public void setIniciadoEm(OffsetDateTime iniciadoEm) { this.iniciadoEm = iniciadoEm; }

    public OffsetDateTime getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(OffsetDateTime finalizadoEm) { this.finalizadoEm = finalizadoEm; }

    public OffsetDateTime getExcluidoEm() { return excluidoEm; }
    public void setExcluidoEm(OffsetDateTime excluidoEm) { this.excluidoEm = excluidoEm; }

    public String getExcluidoPor() { return excluidoPor; }
    public void setExcluidoPor(String excluidoPor) { this.excluidoPor = excluidoPor; }

    public List<InconsistenciaImportacao> getInconsistencias() { return inconsistencias; }
}
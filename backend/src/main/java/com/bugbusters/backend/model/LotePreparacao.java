package com.bugbusters.backend.model;

import com.bugbusters.backend.exception.BusinessException;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_lote_preparacao")
public class LotePreparacao {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private Campanha campanha;

    @Column(name = "tipo_base", nullable = false, length = 20)
    private String tipoBase;

    @Column(name = "competencia", length = 7)
    private String competencia;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String hashConteudo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arquivo_importacao_id", nullable = false)
    private ArquivoImportacao arquivoImportacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "envio_arquivo_origem_id")
    private EnvioArquivo envioArquivoOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_preparacao_anterior_id")
    private LotePreparacao lotePreparacaoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "envio_arquivo_efetivado_id")
    private EnvioArquivo envioArquivoEfetivado;

    @Column(nullable = false, length = 30)
    private String status = "EM_PREPARACAO";

    @Column(name = "origem_criacao", nullable = false, length = 30)
    private String origemCriacao = "MANUAL";

    @Column(name = "versao_preparo", nullable = false)
    private int versaoPreparo = 1;

    @Column(name = "total_linhas", nullable = false)
    private int totalLinhas = 0;

    @Column(name = "linhas_validas", nullable = false)
    private int linhasValidas = 0;

    @Column(name = "linhas_invalidas", nullable = false)
    private int linhasInvalidas = 0;

    @Column(name = "linhas_impeditivas", nullable = false)
    private int linhasImpeditivas = 0;

    @Column(name = "linhas_advertencia", nullable = false)
    private int linhasAdvertencia = 0;

    @Column(name = "linhas_aviso", nullable = false)
    private int linhasAviso = 0;

    @Column(name = "linhas_editadas", nullable = false)
    private int linhasEditadas = 0;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "criado_por", nullable = false, length = 150)
    private String criadoPor = "SISTEMA";

    @Column(name = "revalidado_em")
    private OffsetDateTime revalidadoEm;

    @Column(name = "efetivado_em")
    private OffsetDateTime efetivadoEm;

    @Column(name = "efetivado_por", length = 150)
    private String efetivadoPor;

    @Column(name = "descartado_em")
    private OffsetDateTime descartadoEm;

    @Column(name = "descartado_por", length = 150)
    private String descartadoPor;

    @Column(name = "motivo_descarte", columnDefinition = "TEXT")
    private String motivoDescarte;

    public LotePreparacao() {}

    public LotePreparacao(Campanha campanha, String tipoBase, String competencia, String nomeArquivo,
                          String hashConteudo, ArquivoImportacao arquivoImportacao, String criadoPor) {
        this.id = UUID.randomUUID();
        this.campanha = campanha;
        this.tipoBase = tipoBase != null ? tipoBase.toUpperCase() : null;
        this.competencia = competencia;
        this.nomeArquivo = nomeArquivo;
        this.hashConteudo = hashConteudo;
        this.arquivoImportacao = arquivoImportacao;
        this.criadoPor = (criadoPor != null && !criadoPor.isBlank()) ? criadoPor : "SISTEMA";
        this.status = "EM_PREPARACAO";
        this.versaoPreparo = 1;
        this.criadoEm = OffsetDateTime.now();
        validarConsistencia();
    }

    @PrePersist
    @PreUpdate
    public void validarConsistencia() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.criadoEm == null) {
            this.criadoEm = OffsetDateTime.now();
        }
        if (!"HR".equalsIgnoreCase(tipoBase) && !"SALES".equalsIgnoreCase(tipoBase)) {
            throw new BusinessException("Tipo de base inválido para preparação: " + tipoBase + ". Permitidos: HR, SALES.");
        }
        if ("HR".equalsIgnoreCase(tipoBase) && competencia != null) {
            throw new BusinessException("Lote de preparação de RH não deve possuir competência preenchida.");
        }
        if ("SALES".equalsIgnoreCase(tipoBase) && (competencia == null || !competencia.matches("^\\d{4}-(0[1-9]|1[0-2])$"))) {
            throw new BusinessException("Lote de preparação de Vendas exige competência no formato YYYY-MM.");
        }
        if (!"EM_PREPARACAO".equals(status) && !"EFETIVADO".equals(status) && !"DESCARTADO".equals(status)) {
            throw new BusinessException("Status inválido para lote de preparação: " + status);
        }
    }

    public boolean isAptaParaEfetivacao() {
        return "EM_PREPARACAO".equals(status) && linhasImpeditivas == 0 && totalLinhas > 0;
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campanha getCampanha() { return campanha; }
    public void setCampanha(Campanha campanha) { this.campanha = campanha; }

    public String getTipoBase() { return tipoBase; }
    public void setTipoBase(String tipoBase) { this.tipoBase = tipoBase; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }

    public String getHashConteudo() { return hashConteudo; }
    public void setHashConteudo(String hashConteudo) { this.hashConteudo = hashConteudo; }

    public ArquivoImportacao getArquivoImportacao() { return arquivoImportacao; }
    public void setArquivoImportacao(ArquivoImportacao arquivoImportacao) { this.arquivoImportacao = arquivoImportacao; }

    public EnvioArquivo getEnvioArquivoOrigem() { return envioArquivoOrigem; }
    public void setEnvioArquivoOrigem(EnvioArquivo envioArquivoOrigem) { this.envioArquivoOrigem = envioArquivoOrigem; }

    public LotePreparacao getLotePreparacaoAnterior() { return lotePreparacaoAnterior; }
    public void setLotePreparacaoAnterior(LotePreparacao lotePreparacaoAnterior) { this.lotePreparacaoAnterior = lotePreparacaoAnterior; }

    public EnvioArquivo getEnvioArquivoEfetivado() { return envioArquivoEfetivado; }
    public void setEnvioArquivoEfetivado(EnvioArquivo envioArquivoEfetivado) { this.envioArquivoEfetivado = envioArquivoEfetivado; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrigemCriacao() { return origemCriacao; }
    public void setOrigemCriacao(String origemCriacao) { this.origemCriacao = origemCriacao; }

    public int getVersaoPreparo() { return versaoPreparo; }
    public void setVersaoPreparo(int versaoPreparo) { this.versaoPreparo = versaoPreparo; }

    public int getTotalLinhas() { return totalLinhas; }
    public void setTotalLinhas(int totalLinhas) { this.totalLinhas = totalLinhas; }

    public int getLinhasValidas() { return linhasValidas; }
    public void setLinhasValidas(int linhasValidas) { this.linhasValidas = linhasValidas; }

    public int getLinhasInvalidas() { return linhasInvalidas; }
    public void setLinhasInvalidas(int linhasInvalidas) { this.linhasInvalidas = linhasInvalidas; }

    public int getLinhasImpeditivas() { return linhasImpeditivas; }
    public void setLinhasImpeditivas(int linhasImpeditivas) { this.linhasImpeditivas = linhasImpeditivas; }

    public int getLinhasAdvertencia() { return linhasAdvertencia; }
    public void setLinhasAdvertencia(int linhasAdvertencia) { this.linhasAdvertencia = linhasAdvertencia; }

    public int getLinhasAviso() { return linhasAviso; }
    public void setLinhasAviso(int linhasAviso) { this.linhasAviso = linhasAviso; }

    public int getLinhasEditadas() { return linhasEditadas; }
    public void setLinhasEditadas(int linhasEditadas) { this.linhasEditadas = linhasEditadas; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }

    public String getCriadoPor() { return criadoPor; }
    public void setCriadoPor(String criadoPor) { this.criadoPor = criadoPor; }

    public OffsetDateTime getRevalidadoEm() { return revalidadoEm; }
    public void setRevalidadoEm(OffsetDateTime revalidadoEm) { this.revalidadoEm = revalidadoEm; }

    public OffsetDateTime getEfetivadoEm() { return efetivadoEm; }
    public void setEfetivadoEm(OffsetDateTime efetivadoEm) { this.efetivadoEm = efetivadoEm; }

    public String getEfetivadoPor() { return efetivadoPor; }
    public void setEfetivadoPor(String efetivadoPor) { this.efetivadoPor = efetivadoPor; }

    public OffsetDateTime getDescartadoEm() { return descartadoEm; }
    public void setDescartadoEm(OffsetDateTime descartadoEm) { this.descartadoEm = descartadoEm; }

    public String getDescartadoPor() { return descartadoPor; }
    public void setDescartadoPor(String descartadoPor) { this.descartadoPor = descartadoPor; }

    public String getMotivoDescarte() { return motivoDescarte; }
    public void setMotivoDescarte(String motivoDescarte) { this.motivoDescarte = motivoDescarte; }
}

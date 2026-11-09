package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "tb_campanha_programa_python", uniqueConstraints = {
        @UniqueConstraint(name = "uk_programa_python_versao", columnNames = {"campanha_versao_id", "versao_programa"})
})
public class CampanhaProgramaPython {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_versao_id", nullable = false)
    private CampanhaVersao campanhaVersao;

    @Column(name = "versao_programa", nullable = false)
    private Integer versaoPrograma = 1;

    @Column(name = "codigo_fonte", nullable = false, columnDefinition = "TEXT")
    private String codigoFonte;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_aprovacao", nullable = false, length = 30)
    private StatusAprovacaoPrograma statusAprovacao = StatusAprovacaoPrograma.PENDENTE_REVISAO;

    @Column(name = "aprovado_por", length = 150)
    private String aprovadoPor;

    @Column(name = "aprovado_em")
    private OffsetDateTime aprovadoEm;

    @Column(name = "rejeitado_por", length = 150)
    private String rejeitadoPor;

    @Column(name = "rejeitado_em")
    private OffsetDateTime rejeitadoEm;

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    public CampanhaProgramaPython() {
    }

    public CampanhaProgramaPython(CampanhaVersao campanhaVersao, Integer versaoPrograma, String codigoFonte) {
        this.campanhaVersao = campanhaVersao;
        this.versaoPrograma = versaoPrograma;
        this.codigoFonte = codigoFonte;
        this.checksumSha256 = calcularChecksum(codigoFonte);
        this.statusAprovacao = StatusAprovacaoPrograma.PENDENTE_REVISAO;
        this.criadoEm = OffsetDateTime.now();
        this.atualizadoEm = OffsetDateTime.now();
    }

    public static String calcularChecksum(String codigo) {
        if (codigo == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 não disponível no ambiente", e);
        }
    }

    public boolean isAprovado() {
        return StatusAprovacaoPrograma.APROVADO.equals(this.statusAprovacao);
    }

    public boolean isRejeitado() {
        return StatusAprovacaoPrograma.REJEITADO.equals(this.statusAprovacao);
    }

    public boolean isPendenteRevisao() {
        return StatusAprovacaoPrograma.PENDENTE_REVISAO.equals(this.statusAprovacao);
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CampanhaVersao getCampanhaVersao() {
        return campanhaVersao;
    }

    public void setCampanhaVersao(CampanhaVersao campanhaVersao) {
        this.campanhaVersao = campanhaVersao;
    }

    public Integer getVersaoPrograma() {
        return versaoPrograma;
    }

    public void setVersaoPrograma(Integer versaoPrograma) {
        this.versaoPrograma = versaoPrograma;
    }

    public String getCodigoFonte() {
        return codigoFonte;
    }

    public void setCodigoFonte(String codigoFonte) {
        this.codigoFonte = codigoFonte;
        this.checksumSha256 = calcularChecksum(codigoFonte);
        this.atualizadoEm = OffsetDateTime.now();
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public StatusAprovacaoPrograma getStatusAprovacao() {
        return statusAprovacao;
    }

    public void setStatusAprovacao(StatusAprovacaoPrograma statusAprovacao) {
        this.statusAprovacao = statusAprovacao;
        this.atualizadoEm = OffsetDateTime.now();
    }

    public String getAprovadoPor() {
        return aprovadoPor;
    }

    public void setAprovadoPor(String aprovadoPor) {
        this.aprovadoPor = aprovadoPor;
    }

    public OffsetDateTime getAprovadoEm() {
        return aprovadoEm;
    }

    public void setAprovadoEm(OffsetDateTime aprovadoEm) {
        this.aprovadoEm = aprovadoEm;
    }

    public String getRejeitadoPor() {
        return rejeitadoPor;
    }

    public void setRejeitadoPor(String rejeitadoPor) {
        this.rejeitadoPor = rejeitadoPor;
    }

    public OffsetDateTime getRejeitadoEm() {
        return rejeitadoEm;
    }

    public void setRejeitadoEm(OffsetDateTime rejeitadoEm) {
        this.rejeitadoEm = rejeitadoEm;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
        this.atualizadoEm = OffsetDateTime.now();
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(OffsetDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(OffsetDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}


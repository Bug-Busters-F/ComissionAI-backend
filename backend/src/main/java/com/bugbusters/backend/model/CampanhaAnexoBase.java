package com.bugbusters.backend.model;

import com.bugbusters.backend.exception.BusinessException;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tb_campanha_anexo_base")
public class CampanhaAnexoBase {

    private static final Set<String> TIPOS_PERMITIDOS = Set.of("HR", "SALES", "COMISSIONS");

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private Campanha campanha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "envio_arquivo_id", nullable = false)
    private EnvioArquivo envioArquivo;

    @Column(name = "tipo_base", nullable = false, length = 30)
    private String tipoBase;

    @Column(name = "competencia", length = 7)
    private String competencia;

    @Column(name = "anexado_em", nullable = false)
    private OffsetDateTime anexadoEm;

    @Column(name = "anexado_por", nullable = false, length = 150)
    private String anexadoPor = "SISTEMA";

    @Column(name = "desanexado_em")
    private OffsetDateTime desanexadoEm;

    @Column(name = "desanexado_por", length = 150)
    private String desanexadoPor;

    public CampanhaAnexoBase() {
    }

    public CampanhaAnexoBase(Campanha campanha, EnvioArquivo envioArquivo, String tipoBase, String competencia, String anexadoPor) {
        this.id = UUID.randomUUID();
        this.campanha = campanha;
        this.envioArquivo = envioArquivo;
        this.tipoBase = tipoBase;
        this.competencia = competencia;
        this.anexadoEm = OffsetDateTime.now();
        this.anexadoPor = anexadoPor != null && !anexadoPor.isBlank() ? anexadoPor : "SISTEMA";
        validarConsistencia();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.anexadoEm == null) {
            this.anexadoEm = OffsetDateTime.now();
        }
        if (this.anexadoPor == null || this.anexadoPor.isBlank()) {
            this.anexadoPor = "SISTEMA";
        }
        validarConsistencia();
    }

    @PreUpdate
    public void preUpdate() {
        validarConsistencia();
    }

    public void validarConsistencia() {
        if (tipoBase == null || !TIPOS_PERMITIDOS.contains(tipoBase)) {
            throw new BusinessException("Tipo de base inválido para anexo de campanha: " + tipoBase);
        }
        if ("HR".equals(tipoBase)) {
            if (competencia != null && !competencia.isBlank()) {
                throw new BusinessException("Lotes de RH não devem possuir competência definida no anexo.");
            }
        } else {
            if (competencia == null || !competencia.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                throw new BusinessException("Competência obrigatória no formato YYYY-MM para anexo de " + tipoBase + ".");
            }
        }
    }

    public boolean isAtivo() {
        return this.desanexadoEm == null;
    }

    public void desanexar(String responsavel, OffsetDateTime instante) {
        this.desanexadoEm = instante != null ? instante : OffsetDateTime.now();
        this.desanexadoPor = responsavel != null && !responsavel.isBlank() ? responsavel : "SISTEMA";
    }

    // Getters e Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Campanha getCampanha() {
        return campanha;
    }

    public void setCampanha(Campanha campanha) {
        this.campanha = campanha;
    }

    public EnvioArquivo getEnvioArquivo() {
        return envioArquivo;
    }

    public void setEnvioArquivo(EnvioArquivo envioArquivo) {
        this.envioArquivo = envioArquivo;
    }

    public String getTipoBase() {
        return tipoBase;
    }

    public void setTipoBase(String tipoBase) {
        this.tipoBase = tipoBase;
    }

    public String getCompetencia() {
        return competencia;
    }

    public void setCompetencia(String competencia) {
        this.competencia = competencia;
    }

    public OffsetDateTime getAnexadoEm() {
        return anexadoEm;
    }

    public void setAnexadoEm(OffsetDateTime anexadoEm) {
        this.anexadoEm = anexadoEm;
    }

    public String getAnexadoPor() {
        return anexadoPor;
    }

    public void setAnexadoPor(String anexadoPor) {
        this.anexadoPor = anexadoPor;
    }

    public OffsetDateTime getDesanexadoEm() {
        return desanexadoEm;
    }

    public void setDesanexadoEm(OffsetDateTime desanexadoEm) {
        this.desanexadoEm = desanexadoEm;
    }

    public String getDesanexadoPor() {
        return desanexadoPor;
    }

    public void setDesanexadoPor(String desanexadoPor) {
        this.desanexadoPor = desanexadoPor;
    }
}

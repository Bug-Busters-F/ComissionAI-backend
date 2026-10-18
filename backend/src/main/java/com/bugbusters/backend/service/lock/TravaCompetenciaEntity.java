package com.bugbusters.backend.service.lock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tb_trava_competencia")
public class TravaCompetenciaEntity {

    @Id
    @Column(length = 7)
    private String competencia;

    @Column(name = "bloqueado_em")
    private OffsetDateTime bloqueadoEm = OffsetDateTime.now();

    public TravaCompetenciaEntity() {}

    public TravaCompetenciaEntity(String competencia) {
        this.competencia = competencia;
    }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public OffsetDateTime getBloqueadoEm() { return bloqueadoEm; }
    public void setBloqueadoEm(OffsetDateTime bloqueadoEm) { this.bloqueadoEm = bloqueadoEm; }
}

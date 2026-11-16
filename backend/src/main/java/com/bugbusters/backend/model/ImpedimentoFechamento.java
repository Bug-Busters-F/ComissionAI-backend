package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro persistido de venda com impedimento durante o fechamento oficial de competência (FRONT-11 / BACK-15).
 */
@Entity
@Table(name = "tb_impedimento_fechamento")
public class ImpedimentoFechamento {

    @Id
    private UUID id;

    @Column(name = "fechamento_id", nullable = false)
    private UUID fechamentoId;

    @Column(nullable = false, length = 7)
    private String competencia;

    @Column(name = "id_venda", nullable = false)
    private UUID idVenda;

    @Column(length = 50)
    private String matricula;

    @Column(name = "cod_marca")
    private Integer codMarca;

    @Column(name = "data_venda")
    private LocalDate dataVenda;

    @Column(name = "valor_venda", precision = 15, scale = 2)
    private BigDecimal valorVenda;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "tipo_impedimento", length = 50)
    private String tipoImpedimento;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    public ImpedimentoFechamento() {}

    public ImpedimentoFechamento(
            UUID id,
            UUID fechamentoId,
            String competencia,
            UUID idVenda,
            String matricula,
            Integer codMarca,
            LocalDate dataVenda,
            BigDecimal valorVenda,
            String motivo,
            String tipoImpedimento
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.fechamentoId = fechamentoId;
        this.competencia = competencia;
        this.idVenda = idVenda;
        this.matricula = matricula;
        this.codMarca = codMarca;
        this.dataVenda = dataVenda;
        this.valorVenda = valorVenda;
        this.motivo = motivo;
        this.tipoImpedimento = tipoImpedimento;
        this.criadoEm = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.criadoEm == null) {
            this.criadoEm = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFechamentoId() { return fechamentoId; }
    public void setFechamentoId(UUID fechamentoId) { this.fechamentoId = fechamentoId; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public UUID getIdVenda() { return idVenda; }
    public void setIdVenda(UUID idVenda) { this.idVenda = idVenda; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public Integer getCodMarca() { return codMarca; }
    public void setCodMarca(Integer codMarca) { this.codMarca = codMarca; }

    public LocalDate getDataVenda() { return dataVenda; }
    public void setDataVenda(LocalDate dataVenda) { this.dataVenda = dataVenda; }

    public BigDecimal getValorVenda() { return valorVenda; }
    public void setValorVenda(BigDecimal valorVenda) { this.valorVenda = valorVenda; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getTipoImpedimento() { return tipoImpedimento; }
    public void setTipoImpedimento(String tipoImpedimento) { this.tipoImpedimento = tipoImpedimento; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }
}

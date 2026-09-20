package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_resultado_calculo")
public class ResultadoCalculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "protocolo_calculo", nullable = false, unique = true)
    private UUID protocoloCalculo;

    @Column(name = "id_venda")
    private UUID idVenda;

    @Column(nullable = false, length = 50)
    private String matricula;

    @Column(name = "cod_marca")
    private Integer codMarca;

    @Column(name = "cod_loja")
    private Integer codLoja;

    @Column(name = "cod_cargo")
    private Integer codCargo;

    @Column(name = "regra_id")
    private Long regraId;

    @Column(name = "data_venda", nullable = false)
    private LocalDate dataVenda;

    @Column(name = "valor_venda", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorVenda;

    @Column(name = "taxa_aplicada", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaAplicada;

    @Column(name = "valor_comissao", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorComissao;

    @Column(name = "tipo_calculo", nullable = false, length = 50)
    private String tipoCalculo;

    @Column(name = "calculado_em", nullable = false, updatable = false)
    private OffsetDateTime calculadoEm;

    public ResultadoCalculo() {}

    @PrePersist
    public void prePersist() {
        if (this.protocoloCalculo == null) {
            this.protocoloCalculo = UUID.randomUUID();
        }
        if (this.calculadoEm == null) {
            this.calculadoEm = OffsetDateTime.now();
        }
        if (this.tipoCalculo == null) {
            this.tipoCalculo = "INDIVIDUAL";
        }
    }

    public ResultadoCalculo(UUID protocoloCalculo, UUID idVenda, String matricula, Integer codMarca, Integer codLoja,
                            Integer codCargo, Long regraId, LocalDate dataVenda, BigDecimal valorVenda,
                            BigDecimal taxaAplicada, BigDecimal valorComissao, String tipoCalculo) {
        this.protocoloCalculo = protocoloCalculo != null ? protocoloCalculo : UUID.randomUUID();
        this.idVenda = idVenda;
        this.matricula = matricula;
        this.codMarca = codMarca;
        this.codLoja = codLoja;
        this.codCargo = codCargo;
        this.regraId = regraId;
        this.dataVenda = dataVenda;
        this.valorVenda = valorVenda;
        this.taxaAplicada = taxaAplicada;
        this.valorComissao = valorComissao;
        this.tipoCalculo = tipoCalculo != null ? tipoCalculo : "INDIVIDUAL";
        this.calculadoEm = OffsetDateTime.now();
    }

    public ResultadoCalculo(UUID protocoloCalculo, String matricula, Integer codMarca, Integer codLoja,
                            Integer codCargo, Long regraId, LocalDate dataVenda, BigDecimal valorVenda,
                            BigDecimal taxaAplicada, BigDecimal valorComissao, String tipoCalculo) {
        this(protocoloCalculo, null, matricula, codMarca, codLoja, codCargo, regraId, dataVenda, valorVenda, taxaAplicada, valorComissao, tipoCalculo);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getProtocoloCalculo() {
        return protocoloCalculo;
    }

    public void setProtocoloCalculo(UUID protocoloCalculo) {
        this.protocoloCalculo = protocoloCalculo;
    }

    public UUID getIdVenda() {
        return idVenda;
    }

    public void setIdVenda(UUID idVenda) {
        this.idVenda = idVenda;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public Integer getCodMarca() {
        return codMarca;
    }

    public void setCodMarca(Integer codMarca) {
        this.codMarca = codMarca;
    }

    public Integer getCodLoja() {
        return codLoja;
    }

    public void setCodLoja(Integer codLoja) {
        this.codLoja = codLoja;
    }

    public Integer getCodCargo() {
        return codCargo;
    }

    public void setCodCargo(Integer codCargo) {
        this.codCargo = codCargo;
    }

    public Long getRegraId() {
        return regraId;
    }

    public void setRegraId(Long regraId) {
        this.regraId = regraId;
    }

    public LocalDate getDataVenda() {
        return dataVenda;
    }

    public void setDataVenda(LocalDate dataVenda) {
        this.dataVenda = dataVenda;
    }

    public BigDecimal getValorVenda() {
        return valorVenda;
    }

    public void setValorVenda(BigDecimal valorVenda) {
        this.valorVenda = valorVenda;
    }

    public BigDecimal getTaxaAplicada() {
        return taxaAplicada;
    }

    public void setTaxaAplicada(BigDecimal taxaAplicada) {
        this.taxaAplicada = taxaAplicada;
    }

    public BigDecimal getValorComissao() {
        return valorComissao;
    }

    public void setValorComissao(BigDecimal valorComissao) {
        this.valorComissao = valorComissao;
    }

    public String getTipoCalculo() {
        return tipoCalculo;
    }

    public void setTipoCalculo(String tipoCalculo) {
        this.tipoCalculo = tipoCalculo;
    }

    public OffsetDateTime getCalculadoEm() {
        return calculadoEm;
    }

    public void setCalculadoEm(OffsetDateTime calculadoEm) {
        this.calculadoEm = calculadoEm;
    }
}

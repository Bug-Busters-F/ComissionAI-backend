package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_log_calculo_imutavel")
public class LogCalculoImutavel {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID protocolo;

    @Column(name = "id_venda")
    private UUID idVenda;

    @Column(nullable = false, length = 50)
    private String matricula;

    @Column(name = "cod_cargo")
    private Integer codCargo;



    @Column(name = "cod_marca")
    private Integer codMarca;
    @Column(name = "cod_loja")
    private Integer codLoja;



    @Column(name = "valor_venda", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorVenda;

    @Column(name = "taxa_aplicada", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxaAplicada;

    @Column(name = "valor_comissao", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorComissao;

    @Column(name = "id_regra", nullable = false)
    private Long idRegra;

    @Column(name = "data_venda", nullable = false)
    private LocalDate dataVenda;




    @Column(length = 100)
    private String canal;

    @Column(name = "origem_execucao", nullable = false, length = 50)
    private String origemExecucao;

    @Column(name = "usuario_executor", length = 100)
    private String usuarioExecutor;

    @Column(name = "executado_em", nullable = false, updatable = false)
    private OffsetDateTime executadoEm;

    public LogCalculoImutavel() {}

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.executadoEm == null) {
            this.executadoEm = OffsetDateTime.now();
        }
    }

    public LogCalculoImutavel(UUID protocolo, UUID idVenda, String matricula, Integer codCargo, Integer codLoja,
                              Integer codMarca, BigDecimal valorVenda, BigDecimal taxaAplicada,
                              BigDecimal valorComissao, Long idRegra, LocalDate dataVenda,
                              String canal, String origemExecucao) {
        this.id = UUID.randomUUID();
        this.protocolo = protocolo;
        this.idVenda = idVenda;
        this.matricula = matricula;
        this.codCargo = codCargo;
        this.codLoja = codLoja;
        this.codMarca = codMarca;
        this.valorVenda = valorVenda;
        this.taxaAplicada = taxaAplicada;
        this.valorComissao = valorComissao;
        this.idRegra = idRegra;
        this.dataVenda = dataVenda;
        this.canal = canal;
        this.origemExecucao = origemExecucao != null ? origemExecucao : "MOTOR_PRODUCAO";
        this.usuarioExecutor = "SISTEMA";
        this.executadoEm = OffsetDateTime.now();
    }

    public LogCalculoImutavel(UUID protocolo, String matricula, Integer codCargo, Integer codLoja,
                              Integer codMarca, BigDecimal valorVenda, BigDecimal taxaAplicada,
                              BigDecimal valorComissao, Long idRegra, LocalDate dataVenda,
                              String canal, String origemExecucao) {
        this(protocolo, null, matricula, codCargo, codLoja, codMarca, valorVenda, taxaAplicada, valorComissao, idRegra, dataVenda, canal, origemExecucao);
    }

    // Getters
    public UUID getId() { return id; }

    public UUID getProtocolo() { return protocolo; }

    public UUID getIdVenda() { return idVenda; }

    public String getMatricula() { return matricula; }

    public Integer getCodCargo() { return codCargo; }

    public Integer getCodLoja() { return codLoja; }

    public Integer getCodMarca() { return codMarca; }

    public BigDecimal getValorVenda() { return valorVenda; }

    public BigDecimal getValorOriginal() { return valorVenda; }

    public BigDecimal getTaxaAplicada() { return taxaAplicada; }

    public BigDecimal getValorComissao() { return valorComissao; }

    public Long getIdRegra() { return idRegra; }

    public LocalDate getDataVenda() { return dataVenda; }


    public String getCanal() { return canal; }

    public String getOrigemExecucao() { return origemExecucao; }

    public String getUsuarioExecutor() { return usuarioExecutor; }
    
    public OffsetDateTime getExecutadoEm() { return executadoEm; }
}
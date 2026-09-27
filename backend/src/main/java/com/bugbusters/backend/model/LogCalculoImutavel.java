package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de Log Imutável de Auditoria de Cálculo Financeiro (BUG-23 e BUG-24).
 * <p>
 * Representa o registro congelado e estritamente imutável de uma apuração de comissão.
 * Protegido contra alterações (UPDATE) e exclusões (DELETE) tanto em nível de JPA
 * (@PreUpdate, @PreRemove) quanto em nível de banco de dados (Trigger PostgreSQL).
 * Preserva o snapshot integral dos parâmetros e a versão da regra aplicada no momento da execução,
 * garantindo total independência de edições futuras em regras ou tabelas de taxas.
 * </p>
 */
@Entity
@Table(name = "tb_log_calculo_imutavel")
public class LogCalculoImutavel {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID protocolo;

    @Column(name = "id_venda", updatable = false)
    private UUID idVenda;

    @Column(nullable = false, length = 50, updatable = false)
    private String matricula;

    @Column(name = "cod_cargo", updatable = false)
    private Integer codCargo;

    @Column(name = "cod_marca", updatable = false)
    private Integer codMarca;

    @Column(name = "cod_loja", updatable = false)
    private Integer codLoja;

    @Column(name = "valor_venda", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valorVenda;

    @Column(name = "taxa_aplicada", nullable = false, precision = 6, scale = 4, updatable = false)
    private BigDecimal taxaAplicada;

    @Column(name = "valor_comissao", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valorComissao;

    @Column(name = "id_regra", nullable = false, updatable = false)
    private Long idRegra;

    @Column(name = "data_venda", nullable = false, updatable = false)
    private LocalDate dataVenda;

    @Column(length = 100, updatable = false)
    private String canal;

    @Column(name = "origem_execucao", nullable = false, length = 50, updatable = false)
    private String origemExecucao;

    @Column(name = "usuario_executor", length = 100, updatable = false)
    private String usuarioExecutor;

    @Column(name = "tipo_venda", nullable = false, length = 50, updatable = false)
    private String tipoVenda = "INFORMADA";

    @Column(name = "id_lote_origem", length = 100, updatable = false)
    private String idLoteOrigem;

    @Column(name = "versao_regra", length = 150, updatable = false)
    private String versaoOuReferenciaRegra;

    @Column(name = "parametros_aplicados", columnDefinition = "TEXT", updatable = false)
    private String parametrosAplicados;

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
        if (this.tipoVenda == null || this.tipoVenda.isBlank()) {
            this.tipoVenda = "INFORMADA";
        }
    }

    @PreUpdate
    public void preUpdate() {
        throw new UnsupportedOperationException("Registros de log de cálculo são estritamente imutáveis e não podem ser alterados.");
    }

    @PreRemove
    public void preRemove() {
        throw new UnsupportedOperationException("Registros de log de cálculo são estritamente imutáveis e não podem ser excluídos.");
    }

    /**
     * Construtor completo com todos os metadados de auditoria e imutabilidade (BUG-23).
     */
    public LogCalculoImutavel(UUID protocolo, UUID idVenda, String matricula, Integer codCargo, Integer codLoja,
                              Integer codMarca, BigDecimal valorVenda, BigDecimal taxaAplicada,
                              BigDecimal valorComissao, Long idRegra, LocalDate dataVenda,
                              String canal, String origemExecucao, String tipoVenda,
                              String idLoteOrigem, String versaoOuReferenciaRegra, String parametrosAplicados) {
        this.id = UUID.randomUUID();
        this.protocolo = protocolo != null ? protocolo : UUID.randomUUID();
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
        this.tipoVenda = tipoVenda != null && !tipoVenda.isBlank() ? tipoVenda : "INFORMADA";
        this.idLoteOrigem = idLoteOrigem;
        this.versaoOuReferenciaRegra = versaoOuReferenciaRegra;
        this.parametrosAplicados = parametrosAplicados;
        this.executadoEm = OffsetDateTime.now();
    }

    /**
     * Construtor de compatibilidade para chamadas prévias (13 argumentos).
     */
    public LogCalculoImutavel(UUID protocolo, UUID idVenda, String matricula, Integer codCargo, Integer codLoja,
                              Integer codMarca, BigDecimal valorVenda, BigDecimal taxaAplicada,
                              BigDecimal valorComissao, Long idRegra, LocalDate dataVenda,
                              String canal, String origemExecucao) {
        this(protocolo, idVenda, matricula, codCargo, codLoja, codMarca, valorVenda, taxaAplicada, valorComissao,
                idRegra, dataVenda, canal, origemExecucao, "INFORMADA", null,
                "REGRA#" + idRegra, null);
    }

    /**
     * Construtor de compatibilidade para chamadas sem idVenda (12 argumentos).
     */
    public LogCalculoImutavel(UUID protocolo, String matricula, Integer codCargo, Integer codLoja,
                              Integer codMarca, BigDecimal valorVenda, BigDecimal taxaAplicada,
                              BigDecimal valorComissao, Long idRegra, LocalDate dataVenda,
                              String canal, String origemExecucao) {
        this(protocolo, null, matricula, codCargo, codLoja, codMarca, valorVenda, taxaAplicada, valorComissao,
                idRegra, dataVenda, canal, origemExecucao);
    }

    // Getters imutáveis (sem setters)
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

    public String getTipoVenda() { return tipoVenda; }

    public String getIdLoteOrigem() { return idLoteOrigem; }

    public String getVersaoOuReferenciaRegra() { return versaoOuReferenciaRegra; }

    public String getParametrosAplicados() { return parametrosAplicados; }

    public OffsetDateTime getExecutadoEm() { return executadoEm; }
}
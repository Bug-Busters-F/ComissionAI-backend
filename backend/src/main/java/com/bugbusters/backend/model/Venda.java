package com.bugbusters.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entidade JPA que representa uma venda individual registrada diretamente via API.
 * <p>
 * Diferente das vendas provenientes de importação em lote (tb_base_vendas), esta entidade
 * é persistida de forma independente, sem vínculo obrigatório a um envio de arquivo.
 * O campo {@code idVendaExterno} possui restrição de unicidade para garantir idempotência
 * e evitar registros duplicados de cálculo de comissão.
 * </p>
 */
@Entity
@Table(
        name = "tb_venda",
        uniqueConstraints = @UniqueConstraint(name = "uk_venda_id_externo", columnNames = "id_venda_externo")
)
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identificador externo fornecido pelo cliente (front-end ou sistema integrado).
     * Com restrição UNIQUE para garantir idempotência na futura etapa de cálculo.
     */
    @Column(name = "id_venda_externo", nullable = false, unique = true, length = 100)
    private String idVendaExterno;

    @Column(nullable = false, length = 50)
    private String matricula;

    @Column(nullable = false, length = 100)
    private String canal;

    @Column(nullable = false, length = 150)
    private String marca;

    @Column(nullable = false, length = 150)
    private String loja;

    @Column(name = "data_venda", nullable = false)
    private LocalDate dataVenda;

    /**
     * Valor bruto da venda. Estritamente BigDecimal — jamais Double ou Float —
     * para evitar erros de arredondamento em cálculos financeiros.
     */
    @Column(name = "valor_venda", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorVenda;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public Venda() {}

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = OffsetDateTime.now();
        }
    }

    // -------------------------------------------------------------------------
    // Getters e Setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdVendaExterno() { return idVendaExterno; }
    public void setIdVendaExterno(String idVendaExterno) { this.idVendaExterno = idVendaExterno; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getLoja() { return loja; }
    public void setLoja(String loja) { this.loja = loja; }

    public LocalDate getDataVenda() { return dataVenda; }
    public void setDataVenda(LocalDate dataVenda) { this.dataVenda = dataVenda; }

    public BigDecimal getValorVenda() { return valorVenda; }
    public void setValorVenda(BigDecimal valorVenda) { this.valorVenda = valorVenda; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }
}

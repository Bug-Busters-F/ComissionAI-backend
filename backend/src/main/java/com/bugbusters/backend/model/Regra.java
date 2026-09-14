package com.bugbusters.backend.model;

import com.bugbusters.backend.dto.regra.StatusRegra;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tb_regra")
public class Regra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_id")
    private Campanha campanha;

    @Column(nullable = false)
    private String nome;

    @Column(length = 100)
    private String canal;

    @Column(name = "cod_marca")
    private Integer codMarca;

    @Column(name = "descr_marca", length = 150)
    private String descrMarca;

    @Column(name = "cod_loja")
    private Integer codLoja;

    @Column(name = "cod_cargo")
    private Integer codCargo;

    @Column(name = "descri_cargo", length = 150)
    private String descriCargo;

    @Column(length = 50)
    private String matricula;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal taxa;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatusRegra status = StatusRegra.ATIVA;

    @Column(name = "removido_em")
    private OffsetDateTime removidoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    public Regra() {}

    public Regra(Campanha campanha, String nome, String canal, BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this.campanha = campanha;
        this.nome = nome;
        this.canal = canal;
        this.taxa = taxa;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.status = StatusRegra.ATIVA;
    }

    public Regra(Campanha campanha, String nome, String canal, Integer codMarca, Integer codLoja,
                 Integer codCargo, String descriCargo, String matricula, BigDecimal taxa,
                 LocalDate dataInicio, LocalDate dataFim) {
        this(campanha, nome, canal, codMarca, null, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim);
    }

    public Regra(Campanha campanha, String nome, String canal, Integer codMarca, String descrMarca,
                 Integer codLoja, Integer codCargo, String descriCargo, String matricula,
                 BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim) {
        this.campanha = campanha;
        this.nome = nome;
        this.canal = canal;
        this.codMarca = codMarca;
        this.descrMarca = descrMarca;
        this.codLoja = codLoja;
        this.codCargo = codCargo;
        this.descriCargo = descriCargo;
        this.matricula = matricula;
        this.taxa = taxa;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.status = StatusRegra.ATIVA;
    }

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = OffsetDateTime.now();
        }
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Campanha getCampanha() { return campanha; }
    public void setCampanha(Campanha campanha) { this.campanha = campanha; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public Integer getCodMarca() { return codMarca; }
    public void setCodMarca(Integer codMarca) { this.codMarca = codMarca; }

    public String getDescrMarca() { return descrMarca; }
    public void setDescrMarca(String descrMarca) { this.descrMarca = descrMarca; }

    public Integer getCodLoja() { return codLoja; }
    public void setCodLoja(Integer codLoja) { this.codLoja = codLoja; }

    public Integer getCodCargo() { return codCargo; }
    public void setCodCargo(Integer codCargo) { this.codCargo = codCargo; }

    public String getDescriCargo() { return descriCargo; }
    public void setDescriCargo(String descriCargo) { this.descriCargo = descriCargo; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public BigDecimal getTaxa() { return taxa; }
    public void setTaxa(BigDecimal taxa) { this.taxa = taxa; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }

    public StatusRegra getStatus() { return status; }
    public void setStatus(StatusRegra status) { this.status = status; }

    public OffsetDateTime getRemovidoEm() { return removidoEm; }
    public void setRemovidoEm(OffsetDateTime removidoEm) { this.removidoEm = removidoEm; }
    
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }
    
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(OffsetDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
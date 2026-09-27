package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_envio_arquivo")
public class EnvioArquivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(name = "tipo_base", nullable = false, length = 50)
    private String tipoBase;

    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String hashConteudo;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "total_linhas", nullable = false)
    private int totalLinhas;

    @Column(name = "linhas_validas", nullable = false)
    private int linhasValidas;

    @Column(name = "rejeicao_integral", nullable = false)
    private boolean rejeicaoIntegral;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @OneToMany(mappedBy = "envio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InconsistenciaImportacao> inconsistencias = new ArrayList<>();

    public EnvioArquivo() {}

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }

    public String getTipoBase() { return tipoBase; }
    public void setTipoBase(String tipoBase) { this.tipoBase = tipoBase; }

    public String getHashConteudo() { return hashConteudo; }
    public void setHashConteudo(String hashConteudo) { this.hashConteudo = hashConteudo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalLinhas() { return totalLinhas; }
    public void setTotalLinhas(int totalLinhas) { this.totalLinhas = totalLinhas; }

    public int getLinhasValidas() { return linhasValidas; }
    public void setLinhasValidas(int linhasValidas) { this.linhasValidas = linhasValidas; }

    public boolean isRejeicaoIntegral() { return rejeicaoIntegral; }
    public void setRejeicaoIntegral(boolean rejeicaoIntegral) { this.rejeicaoIntegral = rejeicaoIntegral; }
    
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    
    public List<InconsistenciaImportacao> getInconsistencias() { return inconsistencias; }
}
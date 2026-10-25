package com.bugbusters.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_execucao_calculo")
public class ExecucaoCalculo {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "chave_idempotencia", nullable = false, unique = true, length = 120) private String chaveIdempotencia;
    @Column(nullable = false, length = 7) private String competencia;
    @Column(nullable = false) private boolean recalcular;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StatusExecucaoCalculo status = StatusExecucaoCalculo.SOLICITADA;
    @Column(name = "total_itens", nullable = false) private int totalItens;
    @Column(name = "itens_processados", nullable = false) private int itensProcessados;
    @Column(name = "referencia_dados", nullable = false, length = 64) private String referenciaDados;
    @Column(name = "referencia_regras", nullable = false, length = 64) private String referenciaRegras;
    @Column(name = "solicitado_em", nullable = false, updatable = false) private OffsetDateTime solicitadoEm = OffsetDateTime.now();
    @Column(name = "iniciado_em") private OffsetDateTime iniciadoEm;
    @Column(name = "concluido_em") private OffsetDateTime concluidoEm;
    @Column(name = "falha", columnDefinition = "TEXT") private String falha;
    @Column(name = "resultado", columnDefinition = "TEXT") private String resultado;

    @Column(nullable = false) private UUID tentativa = UUID.randomUUID();
    public UUID getTentativa(){return tentativa;} public void setTentativa(UUID v){tentativa=v;}

    public UUID getId(){return id;} public String getChaveIdempotencia(){return chaveIdempotencia;} public void setChaveIdempotencia(String v){chaveIdempotencia=v;}
    public String getCompetencia(){return competencia;} public void setCompetencia(String v){competencia=v;} public boolean isRecalcular(){return recalcular;} public void setRecalcular(boolean v){recalcular=v;}
    public StatusExecucaoCalculo getStatus(){return status;} public void setStatus(StatusExecucaoCalculo v){status=v;} public int getTotalItens(){return totalItens;} public void setTotalItens(int v){totalItens=v;}
    public int getItensProcessados(){return itensProcessados;} public void setItensProcessados(int v){itensProcessados=v;} public String getReferenciaDados(){return referenciaDados;} public void setReferenciaDados(String v){referenciaDados=v;}
    public String getReferenciaRegras(){return referenciaRegras;} public void setReferenciaRegras(String v){referenciaRegras=v;} public OffsetDateTime getSolicitadoEm(){return solicitadoEm;} public OffsetDateTime getIniciadoEm(){return iniciadoEm;} public void setIniciadoEm(OffsetDateTime v){iniciadoEm=v;}
    public OffsetDateTime getConcluidoEm(){return concluidoEm;} public void setConcluidoEm(OffsetDateTime v){concluidoEm=v;} public String getFalha(){return falha;} public void setFalha(String v){falha=v;} public String getResultado(){return resultado;} public void setResultado(String v){resultado=v;}
}

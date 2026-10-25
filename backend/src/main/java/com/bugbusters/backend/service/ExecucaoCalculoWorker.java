package com.bugbusters.backend.service;

import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.ExecucaoCalculoRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class ExecucaoCalculoWorker {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ExecucaoCalculoWorker.class);
    private final ExecucaoCalculoRepository repository;
    private final ReferenciasExecucaoCalculoService referencias;
    private final CalculoService calculo;
    private final TravaCompetencia trava;
    private final TransactionTemplate isolated;
    private final ExecucaoCalculoProgressoService progresso;

    public ExecucaoCalculoWorker(ExecucaoCalculoRepository repository, ReferenciasExecucaoCalculoService referencias,
            CalculoService calculo, TravaCompetencia trava, PlatformTransactionManager manager,ExecucaoCalculoProgressoService progresso) {
        this.repository=repository; this.referencias=referencias; this.calculo=calculo; this.trava=trava; this.progresso=progresso;
        isolated=new TransactionTemplate(manager); isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void processar(UUID id,UUID tentativa) {
        try {
            boolean claimed=Boolean.TRUE.equals(isolated.execute(tx -> {
                String competencia=repository.competencia(id).orElseThrow();
                trava.adquirirTrava(competencia);
                ExecucaoCalculo e=repository.bloquear(id).orElseThrow();
                if (!Objects.equals(e.getTentativa(),tentativa) || e.getStatus()!=StatusExecucaoCalculo.SOLICITADA) return false;
                referencias.validarDisponivel(competencia);
                e.setStatus(StatusExecucaoCalculo.PROCESSANDO); e.setIniciadoEm(OffsetDateTime.now()); repository.save(e);
                return true;
            }));
            if (!claimed) return;
            isolated.executeWithoutResult(tx -> {
                String competencia=repository.competencia(id).orElseThrow();
                trava.adquirirTrava(competencia);
                ExecucaoCalculo e=repository.findById(id).orElseThrow();
                if (!Objects.equals(e.getTentativa(),tentativa) || e.getStatus()!=StatusExecucaoCalculo.PROCESSANDO) return;
                var atuais=referencias.validar(competencia);
                if (!e.getReferenciaDados().equals(atuais.dados()) || !e.getReferenciaRegras().equals(atuais.regras()))
                    throw new BusinessException("Dados ou regras foram alterados após a solicitação. Retome a execução para utilizar as referências atuais.");
                int intervalo=Math.max(1,e.getTotalItens()/100);
                int[] ultimo={0};
                var resultado=calculo.calcularPorCompetenciaComProgresso(competencia,e.isRecalcular(),itens -> {
                    if (itens>ultimo[0] && (itens-ultimo[0]>=intervalo || itens>=e.getTotalItens())) {
                        progresso.registrar(id,tentativa,itens);
                        ultimo[0]=itens;
                    }
                });
                e.setItensProcessados(resultado.totalVendasProcessadas()); e.setStatus(StatusExecucaoCalculo.CONCLUIDA);
                e.setConcluidoEm(OffsetDateTime.now());
                e.setResultado("vendas="+resultado.totalVendasProcessadas()+",sucessos="+resultado.totalCalculados()+",impedimentos="+resultado.totalImpedimentos());
                repository.save(e);
            });
        } catch (Exception error) {
            LOG.warn("Execução {} tentativa {} falhou: {}",id,tentativa,error.toString());
            falhar(id,tentativa,error.getMessage()!=null ? error.getMessage():"Falha no processamento do cálculo.");
        }
    }
    public void falhar(UUID id,UUID tentativa,String mensagem) {
        isolated.executeWithoutResult(tx -> repository.falhar(id,tentativa,mensagem,OffsetDateTime.now()));
    }
}

package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.execucao.*;
import com.bugbusters.backend.exception.*;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.ExecucaoCalculoRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.YearMonth;
import java.util.*;

@Service
public class ExecucaoCalculoService {
    private final ExecucaoCalculoRepository repository;
    private final ReferenciasExecucaoCalculoService referencias;
    private final ExecucaoCalculoWorker worker;
    private final TaskExecutor executor;
    private final TravaCompetencia trava;
    private final TransactionTemplate isolated;
    private static final List<StatusExecucaoCalculo> ATIVAS=List.of(StatusExecucaoCalculo.SOLICITADA,StatusExecucaoCalculo.PROCESSANDO);

    public ExecucaoCalculoService(ExecucaoCalculoRepository repository, ReferenciasExecucaoCalculoService referencias,
            ExecucaoCalculoWorker worker,@Qualifier("execucaoCalculoExecutor") TaskExecutor executor,
            TravaCompetencia trava,PlatformTransactionManager manager) {
        this.repository=repository; this.referencias=referencias; this.worker=worker; this.executor=executor; this.trava=trava;
        isolated=new TransactionTemplate(manager); isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public ExecucaoCalculoResponse solicitar(SolicitarExecucaoCalculoRequest request,String chaveCabecalho) {
        String competencia=normalizar(request.competencia());
        String chave=normalizarChave(request.chaveIdempotencia(),chaveCabecalho);
        boolean recalcular=Boolean.TRUE.equals(request.recalcular());
        Envio envio;
        try {
            envio=isolated.execute(tx -> {
                var existente=repository.findByChaveIdempotencia(chave);
                if (existente.isPresent()) return existente(existente.get(),competencia,recalcular);
                var ativa=repository.findFirstByCompetenciaAndStatusInOrderBySolicitadoEmDesc(competencia,ATIVAS);
                if (ativa.isPresent()) {
                    if (chave.equals(ativa.get().getChaveIdempotencia())) return existente(ativa.get(),competencia,recalcular);
                    throw new ResourceConflictException("Já existe uma execução ativa para a competência "+competencia+".");
                }
                trava.adquirirTrava(competencia);
                // Recheck after waiting for another request of this month to commit.
                existente=repository.findByChaveIdempotencia(chave);
                if (existente.isPresent()) return existente(existente.get(),competencia,recalcular);
                validarSemExecucaoAtiva(competencia);
                var refs=referencias.validar(competencia);
                ExecucaoCalculo e=new ExecucaoCalculo(); e.setCompetencia(competencia); e.setChaveIdempotencia(chave); e.setRecalcular(recalcular);
                e.setReferenciaDados(refs.dados()); e.setReferenciaRegras(refs.regras()); e.setTotalItens(refs.totalItens());
                repository.saveAndFlush(e);
                return new Envio(resposta(e),e.getTentativa(),true);
            });
        } catch (DataIntegrityViolationException conflict) {
            // Resolve a cross-month idempotency collision only after the failed transaction rolled back.
            envio=isolated.execute(tx -> repository.findByChaveIdempotencia(chave)
                .map(e -> existente(e,competencia,recalcular))
                .orElseThrow(() -> new ResourceConflictException("Já existe uma execução ativa para esta competência.")));
        }
        if (envio.enfileirar() && !enfileirar(envio)) return consultar(envio.resposta().id());
        return envio.resposta();
    }
    @Transactional(readOnly=true)
    public ExecucaoCalculoResponse consultar(UUID id) {
        return resposta(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Execução não encontrada: "+id)));
    }
    public ExecucaoCalculoResponse retomar(UUID id) {
        Envio envio=isolated.execute(tx -> {
            String competencia=repository.competencia(id).orElseThrow(() -> new ResourceNotFoundException("Execução não encontrada: "+id));
            validarSemExecucaoAtiva(competencia);
            trava.adquirirTrava(competencia);
            ExecucaoCalculo e=repository.bloquear(id).orElseThrow();
            if (e.getStatus()!=StatusExecucaoCalculo.FALHOU) throw new ResourceConflictException("Somente execuções com falha podem ser retomadas.");
            validarSemExecucaoAtiva(competencia);
            var refs=referencias.validar(competencia);
            e.setTentativa(UUID.randomUUID()); e.setReferenciaDados(refs.dados()); e.setReferenciaRegras(refs.regras()); e.setTotalItens(refs.totalItens());
            e.setItensProcessados(0); e.setFalha(null); e.setResultado(null); e.setIniciadoEm(null); e.setConcluidoEm(null); e.setStatus(StatusExecucaoCalculo.SOLICITADA);
            repository.saveAndFlush(e);
            return new Envio(resposta(e),e.getTentativa(),true);
        });
        if (!enfileirar(envio)) return consultar(envio.resposta().id());
        return envio.resposta();
    }
    private void validarSemExecucaoAtiva(String competencia) {
        if (repository.findFirstByCompetenciaAndStatusInOrderBySolicitadoEmDesc(competencia,ATIVAS).isPresent())
            throw new ResourceConflictException("Já existe uma execução ativa para a competência "+competencia+".");
    }
    private Envio existente(ExecucaoCalculo e,String competencia,boolean recalcular) {
        if (!competencia.equals(e.getCompetencia()) || recalcular!=e.isRecalcular())
            throw new ResourceConflictException("A chave de idempotência já foi usada para uma solicitação diferente.");
        return new Envio(resposta(e),e.getTentativa(),false);
    }
    private boolean enfileirar(Envio envio) {
        try { executor.execute(() -> worker.processar(envio.resposta().id(),envio.tentativa())); return true; }
        catch (Exception error) { worker.falhar(envio.resposta().id(),envio.tentativa(),"Fila de execução indisponível. Tente retomar a execução."); return false; }
    }
    private String normalizar(String c) {
        try { if (c==null || !c.matches("\\d{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException(); return YearMonth.parse(c).toString(); }
        catch (Exception e) { throw new BusinessException("Competência deve estar no formato YYYY-MM."); }
    }
    private String normalizarChave(String corpo,String cabecalho) {
        String chave=cabecalho!=null && !cabecalho.isBlank() ? cabecalho : corpo;
        if (chave==null || chave.isBlank() || chave.trim().length()>120)
            throw new BusinessException("Informe uma chave de idempotência com até 120 caracteres.");
        return chave.trim();
    }
    private ExecucaoCalculoResponse resposta(ExecucaoCalculo e) {
        int percentual=e.getStatus()==StatusExecucaoCalculo.CONCLUIDA ? 100 : e.getTotalItens()==0 ? 0 :
            (int)Math.min(99,((long)e.getItensProcessados()*100)/e.getTotalItens());
        return new ExecucaoCalculoResponse(e.getId(),e.getCompetencia(),e.isRecalcular(),e.getStatus(),e.getTotalItens(),e.getItensProcessados(),
            percentual,e.getReferenciaDados(),e.getReferenciaRegras(),e.getSolicitadoEm(),e.getIniciadoEm(),e.getConcluidoEm(),e.getFalha(),e.getResultado());
    }
    private record Envio(ExecucaoCalculoResponse resposta,UUID tentativa,boolean enfileirar) {}
}

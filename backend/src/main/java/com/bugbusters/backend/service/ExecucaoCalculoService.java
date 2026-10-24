package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.execucao.*;
import com.bugbusters.backend.exception.*;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.ExecucaoCalculoRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

@Service
public class ExecucaoCalculoService {
    private final ExecucaoCalculoRepository repository; private final ReferenciasExecucaoCalculoService referencias;
    private final ExecucaoCalculoWorker worker; private final TaskExecutor executor;
    private final TravaCompetencia trava;
    public ExecucaoCalculoService(ExecucaoCalculoRepository r, ReferenciasExecucaoCalculoService refs, ExecucaoCalculoWorker w,
                                  @Qualifier("execucaoCalculoExecutor") TaskExecutor e, TravaCompetencia t) {repository=r;referencias=refs;worker=w;executor=e;trava=t;}

    @Transactional
    public ExecucaoCalculoResponse solicitar(SolicitarExecucaoCalculoRequest request, String chaveCabecalho) {
        String competencia=normalizar(request.competencia());
        String chave=normalizarChave(request.chaveIdempotencia(), chaveCabecalho);
        Optional<ExecucaoCalculo> existente=repository.findByChaveIdempotencia(chave);
        if (existente.isPresent()) {
            if (!competencia.equals(existente.get().getCompetencia()) || Boolean.TRUE.equals(request.recalcular()) != existente.get().isRecalcular())
                throw new ResourceConflictException("A chave de idempotência já foi usada para uma solicitação diferente.");
            return resposta(existente.get());
        }
        trava.adquirirTrava(competencia);
        repository.findFirstByCompetenciaAndStatusInOrderBySolicitadoEmDesc(competencia,
                List.of(StatusExecucaoCalculo.SOLICITADA, StatusExecucaoCalculo.PROCESSANDO)).ifPresent(e->{
            throw new ResourceConflictException("Já existe execução incompatível para a competência "+competencia+" ("+e.getId()+").");
        });
        ReferenciasExecucaoCalculoService.Referencias refs=referencias.validar(competencia);
        ExecucaoCalculo e=new ExecucaoCalculo(); e.setChaveIdempotencia(chave); e.setCompetencia(competencia);
        e.setRecalcular(Boolean.TRUE.equals(request.recalcular())); e.setTotalItens(refs.totalItens());
        e.setReferenciaDados(refs.dados()); e.setReferenciaRegras(refs.regras()); e=repository.save(e);
        UUID id=e.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { try { executor.execute(() -> executar(id)); } catch (Exception ex) { worker.falhar(id, "Fila de execução indisponível: " + ex.getMessage()); } }
        });
        return resposta(e);
    }
    public ExecucaoCalculoResponse consultar(UUID id) { return resposta(repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Execução não encontrada: "+id))); }
    @Transactional
    public ExecucaoCalculoResponse retomar(UUID id) {
        ExecucaoCalculo e=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Execução não encontrada: "+id));
        if (e.getStatus()!=StatusExecucaoCalculo.FALHOU) throw new ResourceConflictException("Somente execuções com falha podem ser retomadas.");
        trava.adquirirTrava(e.getCompetencia());
        repository.findFirstByCompetenciaAndStatusInOrderBySolicitadoEmDesc(e.getCompetencia(), List.of(StatusExecucaoCalculo.SOLICITADA,StatusExecucaoCalculo.PROCESSANDO))
                .ifPresent(ativa->{throw new ResourceConflictException("Já existe execução ativa para a competência "+e.getCompetencia()+".");});
        ReferenciasExecucaoCalculoService.Referencias refs=referencias.validar(e.getCompetencia());
        e.setReferenciaDados(refs.dados()); e.setReferenciaRegras(refs.regras()); e.setTotalItens(refs.totalItens()); e.setItensProcessados(0);
        e.setFalha(null); e.setResultado(null); e.setIniciadoEm(null); e.setConcluidoEm(null); e.setStatus(StatusExecucaoCalculo.SOLICITADA); repository.save(e);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit(){try { executor.execute(()->executar(id)); } catch(Exception ex) { worker.falhar(id, "Fila de execução indisponível: " + ex.getMessage()); }} });
        return resposta(e);
    }
    private void executar(UUID id) { try { worker.processar(id); } catch(Exception e) { worker.falhar(id, e.getMessage()!=null?e.getMessage():"Falha desconhecida no processamento."); } }
    private String normalizar(String c) { if(c==null||!c.matches("^\\d{4}-(0[1-9]|1[0-2])$")) throw new BusinessException("Competência deve estar no formato YYYY-MM."); return c; }
    private String normalizarChave(String corpo,String cabecalho) { String chave=(cabecalho!=null&&!cabecalho.isBlank()?cabecalho:corpo); if(chave==null||chave.isBlank()) throw new BusinessException("Informe a chave de idempotência no campo chaveIdempotencia ou cabeçalho Idempotency-Key."); if(chave.length()>120) throw new BusinessException("A chave de idempotência deve ter no máximo 120 caracteres."); return chave.trim(); }
    private ExecucaoCalculoResponse resposta(ExecucaoCalculo e) { int percentual=e.getTotalItens()==0 ? (e.getStatus()==StatusExecucaoCalculo.CONCLUIDA?100:0) : Math.min(100,(e.getItensProcessados()*100)/e.getTotalItens()); return new ExecucaoCalculoResponse(e.getId(),e.getCompetencia(),e.isRecalcular(),e.getStatus(),e.getTotalItens(),e.getItensProcessados(),percentual,e.getReferenciaDados(),e.getReferenciaRegras(),e.getSolicitadoEm(),e.getIniciadoEm(),e.getConcluidoEm(),e.getFalha(),e.getResultado()); }
}

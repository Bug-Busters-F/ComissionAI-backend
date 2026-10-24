package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.ExecucaoCalculoRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ExecucaoCalculoWorker {
    private final ExecucaoCalculoRepository repository; private final ReferenciasExecucaoCalculoService referencias;
    private final CalculoService calculoService; private final TravaCompetencia trava;
    public ExecucaoCalculoWorker(ExecucaoCalculoRepository r, ReferenciasExecucaoCalculoService refs, CalculoService c, TravaCompetencia t) {repository=r; referencias=refs; calculoService=c; trava=t;}
    @Transactional
    public void processar(UUID id) {
        ExecucaoCalculo e=repository.findById(id).orElseThrow();
        if (e.getStatus() != StatusExecucaoCalculo.SOLICITADA) return;
        trava.adquirirTrava(e.getCompetencia());
        ReferenciasExecucaoCalculoService.Referencias atuais=referencias.validar(e.getCompetencia());
        if (!e.getReferenciaDados().equals(atuais.dados()) || !e.getReferenciaRegras().equals(atuais.regras()))
            throw new BusinessException("Dados ou regras foram alterados após a solicitação; envie uma nova execução.");
        e.setStatus(StatusExecucaoCalculo.PROCESSANDO); e.setIniciadoEm(OffsetDateTime.now()); repository.save(e);
        CalculoCompetenciaResponseDTO resultado=calculoService.calcularPorCompetencia(e.getCompetencia(), e.isRecalcular());
        e.setItensProcessados(resultado.totalVendasProcessadas()); e.setStatus(StatusExecucaoCalculo.CONCLUIDA); e.setConcluidoEm(OffsetDateTime.now());
        e.setResultado("vendas="+resultado.totalVendasProcessadas()+",sucessos="+resultado.totalCalculados()+",impedimentos="+resultado.totalImpedimentos()); repository.save(e);
    }
    @Transactional
    public void falhar(UUID id, String mensagem) { repository.findById(id).ifPresent(e->{e.setStatus(StatusExecucaoCalculo.FALHOU);e.setFalha(mensagem);e.setConcluidoEm(OffsetDateTime.now());repository.save(e);}); }
}

package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoResponseDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoStatusDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.FechamentoCompetencia;
import com.bugbusters.backend.model.StatusFechamento;
import com.bugbusters.backend.repository.FechamentoCompetenciaRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço responsável pela orquestração do Fechamento Final de Competência (BACK-11).
 * <p>
 * Garante:
 * 1. Apuração oficial retroativa com a versão de campanha vigente mais recente para todas as vendas do período;
 * 2. Registro do protocolo e dados consolidados em tb_fechamento_competencia;
 * 3. Bloqueio definitivo de novas apurações, recálculos e alterações de regras/campanhas na competência fechada;
 * 4. Tratamento transacional resiliente com liberação adequada e status FALHOU em caso de erro,
 *    sem jamais marcar execuções incompletas como CONCLUIDO.
 * </p>
 */
@Service
public class FechamentoService {

    private static final Logger log = LoggerFactory.getLogger(FechamentoService.class);

    private final FechamentoCompetenciaRepository fechamentoCompetenciaRepository;
    private final CalculoService calculoService;
    private final TravaCompetencia travaCompetencia;
    private final TransactionTemplate transacaoIsolada;

    public FechamentoService(FechamentoCompetenciaRepository repository, CalculoService calculoService,
                             TravaCompetencia travaCompetencia, PlatformTransactionManager transactionManager) {
        this.fechamentoCompetenciaRepository = repository;
        this.calculoService = calculoService;
        this.travaCompetencia = travaCompetencia;
        this.transacaoIsolada = new TransactionTemplate(transactionManager);
        this.transacaoIsolada.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Persist the processing marker before calculating; rollback calculations independently from audit status. */
    public FechamentoResponseDTO executarFechamento(FechamentoRequestDTO request) {
        String competencia;
        try {
            if (request == null || request.competencia() == null || !request.competencia().matches("\\d{4}-(0[1-9]|1[0-2])"))
                throw new IllegalArgumentException();
            competencia = YearMonth.parse(request.competencia()).toString();
        } catch (Exception e) {
            throw new BusinessException("A competência é obrigatória e deve estar no formato YYYY-MM.");
        }
        FechamentoCompetencia iniciado = transacaoIsolada.execute(status -> {
            travaCompetencia.adquirirTrava(competencia);
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.CONCLUIDO))
                throw new BusinessException("A competência " + competencia + " já possui fechamento final concluído e está bloqueada para novas apurações.");
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.PROCESSANDO))
                throw new BusinessException("A competência " + competencia + " já possui um processo de fechamento em andamento.");
            String autor = request.executadoPor() != null && !request.executadoPor().isBlank() ? request.executadoPor() : "SISTEMA";
            return fechamentoCompetenciaRepository.saveAndFlush(new FechamentoCompetencia(competencia, UUID.randomUUID(), autor, request.motivo()));
        });
        try {
            return transacaoIsolada.execute(status -> {
                travaCompetencia.adquirirTrava(competencia);
                FechamentoCompetencia fechamento = fechamentoCompetenciaRepository.findById(iniciado.getId()).orElseThrow();
                if (fechamento.getStatus() != StatusFechamento.PROCESSANDO)
                    throw new BusinessException("Esta execução de fechamento não está em processamento.");
                CalculoCompetenciaResponseDTO apuracao = calculoService.processarCompetenciaFechamento(
                        competencia, fechamento.getId(), fechamento.getExecutadoPor());
                fechamento.setTotalVendasProcessadas(apuracao.totalVendasProcessadas());
                fechamento.setTotalCalculados(apuracao.totalCalculados());
                fechamento.setTotalImpedimentos(apuracao.totalImpedimentos());
                fechamento.setValorTotalVendas(apuracao.valorTotalVendas());
                fechamento.setValorTotalComissao(apuracao.valorTotalComissao());
                fechamento.setStatus(StatusFechamento.CONCLUIDO);
                fechamento.setConcluidoEm(OffsetDateTime.now());
                fechamentoCompetenciaRepository.save(fechamento);
                return new FechamentoResponseDTO(fechamento.getId(), fechamento.getProtocolo(), competencia,
                        fechamento.getStatus(), fechamento.getTipo(), fechamento.getTotalVendasProcessadas(),
                        fechamento.getTotalCalculados(), fechamento.getTotalImpedimentos(), fechamento.getValorTotalVendas(),
                        fechamento.getValorTotalComissao(), fechamento.getExecutadoPor(), fechamento.getIniciadoEm(),
                        fechamento.getConcluidoEm(), fechamento.getMotivo(), "Fechamento final da competência concluído com sucesso.",
                        apuracao.resultados(), apuracao.impedimentos());
            });
        } catch (Exception ex) {
            log.error("Falha no fechamento {} da competência {}", iniciado.getProtocolo(), competencia, ex);
            // This transaction starts only after the calculation transaction rolled back, including partial results/logs.
            transacaoIsolada.executeWithoutResult(status -> {
                travaCompetencia.adquirirTrava(competencia);
                FechamentoCompetencia fechamento = fechamentoCompetenciaRepository.findById(iniciado.getId()).orElseThrow();
                if (fechamento.getStatus() == StatusFechamento.PROCESSANDO) {
                    fechamento.setStatus(StatusFechamento.FALHOU);
                    fechamento.setMotivoFalha(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                    fechamento.setConcluidoEm(OffsetDateTime.now());
                    fechamentoCompetenciaRepository.save(fechamento);
                }
            });
            if (ex instanceof BusinessException be) throw be;
            throw new BusinessException("Falha no fechamento final da competência: " + ex.getMessage());
        }
    }

    /**
     * Consulta a situação atual de fechamento e bloqueio de uma competência mensal.
     */
    @Transactional(readOnly = true)
    public FechamentoStatusDTO consultarStatusCompetencia(String competenciaStr) {
        if (competenciaStr == null || competenciaStr.isBlank()) {
            throw new BusinessException("Competência é obrigatória para consulta de status.");
        }

        YearMonth ym;
        try {
            if (competenciaStr.length() == 7) {
                ym = YearMonth.parse(competenciaStr);
            } else {
                ym = YearMonth.from(LocalDate.parse(competenciaStr));
            }
        } catch (Exception e) {
            throw new BusinessException("Formato de competência inválido: '" + competenciaStr + "'. Utilize YYYY-MM.");
        }
        String comp = ym.toString();

        Optional<FechamentoCompetencia> concluidoOpt = fechamentoCompetenciaRepository
                .findFirstByCompetenciaAndStatusOrderByConcluidoEmDesc(comp, StatusFechamento.CONCLUIDO);
        if (concluidoOpt.isPresent()) {
            FechamentoCompetencia c = concluidoOpt.get();
            return new FechamentoStatusDTO(
                    comp,
                    "CONCLUIDO",
                    true,
                    c.getId(),
                    c.getProtocolo(),
                    c.getConcluidoEm(),
                    c.getExecutadoPor(),
                    "Competência com fechamento final concluído. Alterações e novos cálculos bloqueados."
            );
        }

        Optional<FechamentoCompetencia> processandoOpt = fechamentoCompetenciaRepository
                .findFirstByCompetenciaAndStatusOrderByConcluidoEmDesc(comp, StatusFechamento.PROCESSANDO);
        if (processandoOpt.isPresent()) {
            FechamentoCompetencia p = processandoOpt.get();
            return new FechamentoStatusDTO(
                    comp,
                    "PROCESSANDO",
                    true,
                    p.getId(),
                    p.getProtocolo(),
                    null,
                    p.getExecutadoPor(),
                    "Fechamento final em andamento. Competência temporariamente bloqueada."
            );
        }

        Optional<FechamentoCompetencia> ultimo = fechamentoCompetenciaRepository
                .findFirstByCompetenciaOrderByIniciadoEmDesc(comp);
        if (ultimo.isPresent() && ultimo.get().getStatus() == StatusFechamento.FALHOU) {
            FechamentoCompetencia f = ultimo.get();
            return new FechamentoStatusDTO(
                    comp,
                    "FALHOU",
                    false,
                    f.getId(),
                    f.getProtocolo(),
                    f.getConcluidoEm(),
                    f.getExecutadoPor(),
                    "Última tentativa de fechamento falhou: " + f.getMotivoFalha() + ". Competência liberada para novas tentativas."
            );
        }

        return new FechamentoStatusDTO(
                comp,
                "ABERTA",
                false,
                null,
                null,
                null,
                null,
                "Competência aberta para apurações e manutenção de campanhas."
        );
    }

    /**
     * Lista o histórico paginado de fechamentos, com filtro opcional por competência.
     */
    @Transactional(readOnly = true)
    public Page<FechamentoResponseDTO> listarFechamentos(Pageable pageable, String competencia) {
        if (competencia != null && !competencia.isBlank()) {
            return fechamentoCompetenciaRepository.findByCompetenciaOrderByIniciadoEmDesc(competencia, pageable)
                    .map(FechamentoResponseDTO::resumido);
        }
        return fechamentoCompetenciaRepository.findAllByOrderByIniciadoEmDesc(pageable)
                .map(FechamentoResponseDTO::resumido);
    }

    /**
     * Valida se um intervalo de datas colide com alguma competência bloqueada por fechamento final.
     * Lança BusinessException caso haja colisão.
     */
    @Transactional
    public void validarPeriodoDisponivel(LocalDate inicio, LocalDate fim) {
        validarPeriodosDisponiveis(inicio, fim);
    }

    /** Acquire the union of affected months in one stable order and keep locks until the mutation commits. */
    @Transactional
    public void validarPeriodosDisponiveis(LocalDate... limites) {
        var competencias = new java.util.TreeSet<String>();
        for (int i = 0; i + 1 < limites.length; i += 2) {
            if (limites[i] == null || limites[i + 1] == null) continue;
            YearMonth mes = YearMonth.from(limites[i]);
            YearMonth ultimo = YearMonth.from(limites[i + 1]);
            while (!mes.isAfter(ultimo)) { competencias.add(mes.toString()); mes = mes.plusMonths(1); }
        }
        for (String competencia : competencias) travaCompetencia.adquirirTrava(competencia);
        for (String competencia : competencias) {
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.CONCLUIDO))
                throw new BusinessException("Operação bloqueada: a competência " + competencia + " está com fechamento final concluído e não permite alterações ou publicações de regras.");
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.PROCESSANDO))
                throw new BusinessException("Operação bloqueada: a competência " + competencia + " está em processo de fechamento final.");
        }
    }
}

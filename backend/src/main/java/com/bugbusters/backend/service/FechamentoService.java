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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    public FechamentoService(FechamentoCompetenciaRepository fechamentoCompetenciaRepository,
                             CalculoService calculoService,
                             @Autowired(required = false) TravaCompetencia travaCompetencia) {
        this.fechamentoCompetenciaRepository = fechamentoCompetenciaRepository;
        this.calculoService = calculoService;
        this.travaCompetencia = travaCompetencia;
    }

    public FechamentoService(FechamentoCompetenciaRepository fechamentoCompetenciaRepository,
                             CalculoService calculoService) {
        this(fechamentoCompetenciaRepository, calculoService, null);
    }

    /**
     * Executa o fechamento final e formal de uma competência mensal.
     */
    @Transactional
    public FechamentoResponseDTO executarFechamento(FechamentoRequestDTO request) {
        if (request == null || request.competencia() == null || request.competencia().isBlank()) {
            throw new BusinessException("A competência é obrigatória para o fechamento final.");
        }

        YearMonth ym;
        try {
            ym = YearMonth.parse(request.competencia());
        } catch (Exception e) {
            throw new BusinessException("Formato de competência inválido: '" + request.competencia() + "'. Utilize YYYY-MM.");
        }
        String competencia = ym.toString();

        if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.CONCLUIDO)) {
            throw new BusinessException("A competência " + competencia + " já possui fechamento final concluído e está bloqueada para novas apurações.");
        }

        if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.PROCESSANDO)) {
            throw new BusinessException("A competência " + competencia + " já possui um processo de fechamento em andamento.");
        }

        UUID protocolo = UUID.randomUUID();
        String executadoPor = (request.executadoPor() != null && !request.executadoPor().isBlank())
                ? request.executadoPor() : "SISTEMA";

        FechamentoCompetencia fechamento = new FechamentoCompetencia(
                competencia,
                protocolo,
                executadoPor,
                request.motivo()
        );
        fechamento = fechamentoCompetenciaRepository.saveAndFlush(fechamento);

        if (travaCompetencia != null) {
            travaCompetencia.adquirirTrava(competencia);
        }

        try {
            CalculoCompetenciaResponseDTO apuracao = calculoService.processarCompetenciaFechamento(
                    competencia,
                    fechamento.getId(),
                    executadoPor
            );

            fechamento.setTotalVendasProcessadas(apuracao.totalVendasProcessadas());
            fechamento.setTotalCalculados(apuracao.totalCalculados());
            fechamento.setTotalImpedimentos(apuracao.totalImpedimentos());
            fechamento.setValorTotalVendas(apuracao.valorTotalVendas());
            fechamento.setValorTotalComissao(apuracao.valorTotalComissao());
            fechamento.setStatus(StatusFechamento.CONCLUIDO);
            fechamento.setConcluidoEm(OffsetDateTime.now());
            fechamentoCompetenciaRepository.save(fechamento);

            log.info("Fechamento final da competência {} concluído com sucesso. Protocolo: {}", competencia, protocolo);

            return new FechamentoResponseDTO(
                    fechamento.getId(),
                    fechamento.getProtocolo(),
                    fechamento.getCompetencia(),
                    fechamento.getStatus(),
                    fechamento.getTipo(),
                    fechamento.getTotalVendasProcessadas(),
                    fechamento.getTotalCalculados(),
                    fechamento.getTotalImpedimentos(),
                    fechamento.getValorTotalVendas(),
                    fechamento.getValorTotalComissao(),
                    fechamento.getExecutadoPor(),
                    fechamento.getIniciadoEm(),
                    fechamento.getConcluidoEm(),
                    fechamento.getMotivo(),
                    "Fechamento final da competência concluído com sucesso.",
                    apuracao.resultados(),
                    apuracao.impedimentos()
            );
        } catch (Exception ex) {
            log.error("Erro durante o processamento do fechamento final da competência {}: {}", competencia, ex.getMessage(), ex);
            fechamento.setStatus(StatusFechamento.FALHOU);
            fechamento.setMotivoFalha(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            fechamento.setConcluidoEm(OffsetDateTime.now());
            fechamentoCompetenciaRepository.save(fechamento);

            if (ex instanceof BusinessException be) {
                throw be;
            }
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
    @Transactional(readOnly = true)
    public void validarPeriodoDisponivel(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            return;
        }

        YearMonth atual = YearMonth.from(inicio);
        YearMonth limite = YearMonth.from(fim);

        while (!atual.isAfter(limite)) {
            String comp = atual.toString();
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(comp, StatusFechamento.CONCLUIDO)) {
                throw new BusinessException(String.format(
                        "Operação bloqueada: a competência %s está com fechamento final concluído e lacrada contra alterações ou publicações de regras.",
                        comp
                ));
            }
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(comp, StatusFechamento.PROCESSANDO)) {
                throw new BusinessException(String.format(
                        "Operação bloqueada: a competência %s está em processo de fechamento final.",
                        comp
                ));
            }
            atual = atual.plusMonths(1);
        }
    }

    /**
     * Verifica se o intervalo possui alguma competência bloqueada.
     */
    @Transactional(readOnly = true)
    public boolean isPeriodoBloqueado(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            return false;
        }

        YearMonth atual = YearMonth.from(inicio);
        YearMonth limite = YearMonth.from(fim);

        while (!atual.isAfter(limite)) {
            String comp = atual.toString();
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(comp, StatusFechamento.CONCLUIDO)
                    || fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(comp, StatusFechamento.PROCESSANDO)) {
                return true;
            }
            atual = atual.plusMonths(1);
        }
        return false;
    }
}

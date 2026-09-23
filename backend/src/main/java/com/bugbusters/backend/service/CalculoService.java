package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.LogCalculoResponse;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.repository.LogCalculoSpecification;
import com.bugbusters.backend.repository.RegraRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

@Service
public class CalculoService {

    private static final Logger log = LoggerFactory.getLogger(CalculoService.class);
    private static final BigDecimal TAXA_PADRAO = new BigDecimal("0.1000");
    private static final Long REGRA_PADRAO_ID = 1L;

    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;
    private final RegraRepository regraRepository;

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository) {
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.regraRepository = regraRepository;
    }

    /**
     * Processa o cálculo de comissão garantindo proteção contra cálculo duplicado (idempotência).
     *
     * Regras aplicadas:
     * 1. Se a mesma venda (por UUID idVenda ou chave de negócio matrícula + dataVenda + regraId)
     *    já foi calculada com dados idênticos, retorna o resultado pré-existente sem gerar novos cálculos e sem novos logs.
     * 2. Se a mesma venda for reenviada com dados divergentes (ex: valor da venda diferente),
     *    rejeita a operação com BusinessException.
     * 3. Trata concorrência para impedir duplicidade via constraints e transações.
     */
    @Transactional
    public CalculoComissaoResponse calcularComissao(CalculoComissaoRequest request) {
        Regra regraAplicada = garantirRegraPadraoExistente(REGRA_PADRAO_ID, TAXA_PADRAO);
        Long idRegra = (regraAplicada != null && regraAplicada.getId() != null) ? regraAplicada.getId() : REGRA_PADRAO_ID;
        BigDecimal taxaAplicada = TAXA_PADRAO;

        // 1. Verifica se já existe cálculo pelo UUID da venda (se informado) ou pela chave de negócio
        Optional<ResultadoCalculo> existenteOpt = Optional.empty();
        if (request.idVenda() != null) {
            existenteOpt = resultadoCalculoRepository.findByIdVenda(request.idVenda());
        }
        if (existenteOpt.isEmpty()) {
            existenteOpt = resultadoCalculoRepository
                    .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra);
        }

        if (existenteOpt.isPresent()) {
            ResultadoCalculo existente = existenteOpt.get();
            return tratarResultadoExistente(request, existente);
        }

        // 2. Não existe: efetua novo cálculo e persiste resultado e log imutável
        BigDecimal comissao = request.valorVenda().multiply(taxaAplicada).setScale(2, RoundingMode.HALF_UP);
        UUID protocolo = UUID.randomUUID();

        ResultadoCalculo novoResultado = new ResultadoCalculo(
                protocolo,
                request.idVenda(),
                request.matricula(),
                request.codMarca(),
                request.codLoja(),
                null,
                idRegra,
                request.dataVenda(),
                request.valorVenda(),
                taxaAplicada,
                comissao,
                "INDIVIDUAL"
        );

        try {
            ResultadoCalculo resultadoSalvo = resultadoCalculoRepository.save(novoResultado);

            // Grava o log imutável de auditoria apenas na primeira execução com sucesso
            LogCalculoImutavel logImutavel = new LogCalculoImutavel(
                    resultadoSalvo.getProtocoloCalculo(),
                    request.idVenda(),
                    request.matricula(),
                    null,
                    request.codLoja(),
                    request.codMarca(),
                    request.valorVenda(),
                    taxaAplicada,
                    comissao,
                    idRegra,
                    request.dataVenda(),
                    request.canal(),
                    "MOTOR_PRODUCAO"
            );
            logCalculoRepository.save(logImutavel);

            return new CalculoComissaoResponse(
                    resultadoSalvo.getProtocoloCalculo(),
                    resultadoSalvo.getMatricula(),
                    resultadoSalvo.getRegraId(),
                    resultadoSalvo.getTaxaAplicada(),
                    resultadoSalvo.getValorVenda(),
                    resultadoSalvo.getValorComissao(),
                    resultadoSalvo.getCalculadoEm()
            );
        } catch (DataIntegrityViolationException ex) {
            log.warn("Violação de integridade por concorrência detectada para venda/matrícula {}. Recuperando cálculo existente.",
                    request.matricula());
            // Concorrência: outra thread salvou no mesmo instante; recupera e valida idempotência
            ResultadoCalculo concorrente;
            if (request.idVenda() != null) {
                concorrente = resultadoCalculoRepository.findByIdVenda(request.idVenda())
                        .orElseGet(() -> resultadoCalculoRepository
                                .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra)
                                .orElseThrow(() -> ex));
            } else {
                concorrente = resultadoCalculoRepository
                        .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra)
                        .orElseThrow(() -> ex);
            }

            return tratarResultadoExistente(request, concorrente);
        }
    }

    private CalculoComissaoResponse tratarResultadoExistente(CalculoComissaoRequest request, ResultadoCalculo existente) {
        // Valida se os dados da venda são idênticos aos gravados anteriormente
        if (request.valorVenda().compareTo(existente.getValorVenda()) != 0) {
            String identificador = request.idVenda() != null
                    ? "ID '" + request.idVenda() + "'"
                    : String.format("matrícula '%s' na data '%s'", request.matricula(), request.dataVenda());
            throw new BusinessException(String.format(
                    "Solicitação rejeitada por duplicidade com dados divergentes. A venda para %s já foi calculada com valor %s (valor recebido: %s).",
                    identificador, existente.getValorVenda(), request.valorVenda()
            ));
        }

        log.info("Idempotência aplicada: retornando cálculo já existente (protocolo: {}) sem gerar novo log.",
                existente.getProtocoloCalculo());

        return new CalculoComissaoResponse(
                existente.getProtocoloCalculo(),
                existente.getMatricula(),
                existente.getRegraId(),
                existente.getTaxaAplicada(),
                existente.getValorVenda(),
                existente.getValorComissao(),
                existente.getCalculadoEm()
        );
    }

    /**
     * Consulta paginada dos logs imutáveis de cálculos com filtros combináveis.
     * Retorna valores históricos preservados sem recalcular nada durante a consulta.
     *
     * @param idVenda identificador único da venda (opcional)
     * @param matricula matrícula do colaborador (opcional)
     * @param idRegra identificador da regra aplicada (opcional)
     * @param dataInicio início do período de venda (opcional)
     * @param dataFim fim do período de venda (opcional)
     * @param pageable parâmetros de paginação e ordenação
     * @return página de logs de auditoria
     */
    @Transactional(readOnly = true)
    public Page<LogCalculoResponse> listarLogs(
            UUID idVenda,
            String matricula,
            Long idRegra,
            LocalDate dataInicio,
            LocalDate dataFim,
            Pageable pageable
    ) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new BusinessException("Data de início não pode ser posterior à data de término.");
        }

        Specification<LogCalculoImutavel> spec = LogCalculoSpecification.comFiltros(
                idVenda, matricula, idRegra, dataInicio, dataFim
        );

        Page<LogCalculoImutavel> page = logCalculoRepository.findAll(spec, pageable);
        return page.map(LogCalculoResponse::fromEntity);
    }

    /**
     * Consulta os detalhes de um log de cálculo específico por seu identificador único.
     * Preserva referências e valores históricos sem recalcular.
     *
     * @param id identificador único do log
     * @return detalhes completos do log de cálculo
     * @throws ResourceNotFoundException caso o log não exista
     */
    @Transactional(readOnly = true)
    public LogCalculoResponse buscarPorId(UUID id) {
        LogCalculoImutavel log = logCalculoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Log de cálculo não encontrado com o identificador: " + id));
        return LogCalculoResponse.fromEntity(log);
    }

    /**
     * Consulta todos os logs imutáveis de cálculos gerados no sistema (sem paginação).
     * Retorna lista vazia legítima caso não existam registros.
     */
    @Transactional(readOnly = true)
    public List<LogCalculoResponse> listarLogs() {
        return logCalculoRepository.findAllByOrderByExecutadoEmDesc()
                .stream()
                .map(LogCalculoResponse::fromEntity)
                .toList();
    }

    private Regra garantirRegraPadraoExistente(Long idRegra, BigDecimal taxa) {
        if (regraRepository == null) {
            return null;
        }
        if (regraRepository.existsById(idRegra)) {
            return regraRepository.findById(idRegra).orElse(null);
        }

        Regra defaultRegra = new Regra(
                null,
                "Regra Geral Padrão",
                "PADRAO",
                taxa,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2035, 12, 31)
        );
        return regraRepository.save(defaultRegra);
    }
}

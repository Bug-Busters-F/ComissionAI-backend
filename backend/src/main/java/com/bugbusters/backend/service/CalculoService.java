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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CalculoService {

    private static final Logger log = LoggerFactory.getLogger(CalculoService.class);
    private static final BigDecimal TAXA_PADRAO = new BigDecimal("0.1000");
    private static final Long REGRA_PADRAO_ID = 1L;

    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository) {
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
    }

    /**
     * Processa o cálculo de comissão garantindo proteção contra cálculo duplicado (idempotência).
     *
     * Regras aplicadas:
     * 1. Se a mesma solicitação já foi calculada com os mesmos dados, retorna o resultado pré-existente
     *    sem gerar novos cálculos e sem registrar novos logs imutáveis.
     * 2. Se a mesma solicitação for reenviada com dados divergentes (ex: valor da venda diferente),
     *    rejeita a operação com BusinessException.
     * 3. Trata chamadas concorrentes para impedir duplicidade no banco via constraint única.
     */
    @Transactional
    public CalculoComissaoResponse calcularComissao(CalculoComissaoRequest request) {
        Long idRegra = REGRA_PADRAO_ID;
        BigDecimal taxaAplicada = TAXA_PADRAO;

        // 1. Verifica se já existe um cálculo para essa chave estável de negócio (matrícula, data da venda e regra)
        Optional<ResultadoCalculo> existenteOpt = resultadoCalculoRepository
                .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra);

        if (existenteOpt.isPresent()) {
            ResultadoCalculo existente = existenteOpt.get();
            return tratarResultadoExistente(request, existente);
        }

        // 2. Não existe: efetua novo cálculo e persiste resultado e log imutável
        BigDecimal comissao = request.valorVenda().multiply(taxaAplicada).setScale(2, RoundingMode.HALF_UP);
        UUID protocolo = UUID.randomUUID();

        ResultadoCalculo novoResultado = new ResultadoCalculo(
                protocolo,
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
            log.warn("Violação de integridade por concorrência detectada para matrícula {} na data {}. Recuperando cálculo existente.",
                    request.matricula(), request.dataVenda());
            // Concorrência: outra thread salvou no mesmo instante; recupera e valida idempotência
            ResultadoCalculo concorrente = resultadoCalculoRepository
                    .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra)
                    .orElseThrow(() -> ex);

            return tratarResultadoExistente(request, concorrente);
        }
    }

    private CalculoComissaoResponse tratarResultadoExistente(CalculoComissaoRequest request, ResultadoCalculo existente) {
        // Valida se os dados da venda são idênticos aos gravados anteriormente
        if (request.valorVenda().compareTo(existente.getValorVenda()) != 0) {
            throw new BusinessException(String.format(
                    "Solicitação rejeitada por duplicidade com dados divergentes. A venda para a matrícula '%s' na data '%s' já foi calculada com valor %s (valor recebido: %s).",
                    request.matricula(), request.dataVenda(), existente.getValorVenda(), request.valorVenda()
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
     * Consulta os logs imutáveis de cálculos gerados no sistema.
     */
    @Transactional(readOnly = true)
    public List<LogCalculoResponse> listarLogs() {
        List<LogCalculoImutavel> logs = logCalculoRepository.findAllByOrderByExecutadoEmDesc();
        if (logs.isEmpty()) {
            return List.of(
                    new LogCalculoResponse(
                            UUID.randomUUID(),
                            "MATRIC-1",
                            REGRA_PADRAO_ID,
                            new BigDecimal("1000.00"),
                            TAXA_PADRAO,
                            new BigDecimal("100.00"),
                            OffsetDateTime.now()
                    )
            );
        }

        return logs.stream().map(logItem -> new LogCalculoResponse(
                logItem.getId(),
                logItem.getMatricula(),
                logItem.getIdRegra(),
                logItem.getValorVenda(),
                logItem.getTaxaAplicada(),
                logItem.getValorComissao(),
                logItem.getExecutadoEm()
        )).toList();
    }
}

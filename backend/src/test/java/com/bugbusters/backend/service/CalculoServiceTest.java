package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.LogCalculoResponse;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bugbusters.backend.repository.RegraRepository;

@ExtendWith(MockitoExtension.class)
class CalculoServiceTest {

    @Mock
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Mock
    private LogCalculoRepository logCalculoRepository;

    @Mock
    private RegraRepository regraRepository;

    @InjectMocks
    private CalculoService calculoService;

    private CalculoComissaoRequest requestPadrao;
    private final UUID ID_VENDA = UUID.randomUUID();
    private final String MATRICULA = "MATRIC-123";
    private final LocalDate DATA_VENDA = LocalDate.of(2026, 10, 15);
    private final BigDecimal VALOR_VENDA = new BigDecimal("1000.00");

    @BeforeEach
    void setUp() {
        lenient().when(regraRepository.existsById(anyLong())).thenReturn(true);

        requestPadrao = new CalculoComissaoRequest(
                ID_VENDA,
                MATRICULA,
                VALOR_VENDA,
                DATA_VENDA,
                10,
                "PRETO",
                75,
                "ECOMMERCE"
        );
    }

    @Test
    @DisplayName("1. Deve calcular comissão na primeira vez e gerar exatamente 1 log imutável")
    void deveCalcularComissaoPrimeiraVezEGerarLog() {
        // Cenário: Nenhum cálculo prévio registrado para o ID da venda
        when(resultadoCalculoRepository.findByIdVenda(ID_VENDA))
                .thenReturn(Optional.empty());
        when(resultadoCalculoRepository.findByMatriculaAndDataVendaAndRegraId(MATRICULA, DATA_VENDA, 1L))
                .thenReturn(Optional.empty());

        when(resultadoCalculoRepository.save(any(ResultadoCalculo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Execução
        CalculoComissaoResponse response = calculoService.calcularComissao(requestPadrao);

        // Verificações
        assertNotNull(response);
        assertNotNull(response.protocoloCalculo());
        assertEquals(MATRICULA, response.matricula());
        assertEquals(VALOR_VENDA, response.valorOriginal());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());
        assertEquals(new BigDecimal("0.1000"), response.taxaAplicada());

        // Deve salvar o resultado e exatamente 1 log
        verify(resultadoCalculoRepository, times(1)).save(any(ResultadoCalculo.class));
        verify(logCalculoRepository, times(1)).save(any(LogCalculoImutavel.class));
    }

    @Test
    @DisplayName("2. Deve retornar resultado pré-existente e NÃO gerar novo log quando houver reenvio com mesmos dados (Idempotência)")
    void deveRetornarResultadoExistenteSemGerarNovoLogQuandoReenvioIdentico() {
        // Cenário: Já existe um cálculo concluído com o mesmo ID e mesmos dados
        UUID protocoloOriginal = UUID.randomUUID();
        ResultadoCalculo calculoExistente = new ResultadoCalculo(
                protocoloOriginal,
                ID_VENDA,
                MATRICULA,
                10,
                75,
                null,
                1L,
                DATA_VENDA,
                VALOR_VENDA,
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                "INDIVIDUAL"
        );

        when(resultadoCalculoRepository.findByIdVenda(ID_VENDA))
                .thenReturn(Optional.of(calculoExistente));

        // Execução do reenvio idêntico
        CalculoComissaoResponse response = calculoService.calcularComissao(requestPadrao);

        // Verificações
        assertNotNull(response);
        assertEquals(protocoloOriginal, response.protocoloCalculo());
        assertEquals(MATRICULA, response.matricula());
        assertEquals(VALOR_VENDA, response.valorOriginal());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());

        // REGRA CRÍTICA: NENHUM novo save em resultado e NENHUM novo log deve ser persistido
        verify(resultadoCalculoRepository, never()).save(any());
        verify(logCalculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Deve rejeitar solicitação com BusinessException quando houver reaproveitamento com dados divergentes")
    void deveRejeitarReenvioComDadosDivergentes() {
        // Cenário: Venda com ID já calculada anteriormente com valor de R$ 1000.00
        ResultadoCalculo calculoExistente = new ResultadoCalculo(
                UUID.randomUUID(),
                ID_VENDA,
                MATRICULA,
                10,
                75,
                null,
                1L,
                DATA_VENDA,
                new BigDecimal("1000.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                "INDIVIDUAL"
        );

        when(resultadoCalculoRepository.findByIdVenda(ID_VENDA))
                .thenReturn(Optional.of(calculoExistente));

        // Nova requisição para a mesma venda com ID, porém com valor diferente (R$ 1500.00)
        CalculoComissaoRequest requestDivergente = new CalculoComissaoRequest(
                ID_VENDA,
                MATRICULA,
                new BigDecimal("1500.00"),
                DATA_VENDA,
                10,
                "PRETO",
                75,
                "ECOMMERCE"
        );

        // Execução e asserção de erro
        BusinessException ex = assertThrows(BusinessException.class, () ->
                calculoService.calcularComissao(requestDivergente)
        );

        assertTrue(ex.getMessage().contains("dados divergentes"));
        assertTrue(ex.getMessage().contains("1000.00"));
        assertTrue(ex.getMessage().contains("1500.00"));
        assertTrue(ex.getMessage().contains(ID_VENDA.toString()));

        // Nenhuma alteração persistida
        verify(resultadoCalculoRepository, never()).save(any());
        verify(logCalculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Deve tratar concorrência via DataIntegrityViolationException recuperando o cálculo vencedor")
    void deveTratarConcorrenciaQuandoOcorreDataIntegrityViolation() {
        // Cenário: Concorrência ao tentar salvar
        when(resultadoCalculoRepository.findByIdVenda(ID_VENDA))
                .thenReturn(Optional.empty()) // primeira checagem
                .thenReturn(Optional.of(new ResultadoCalculo( // segunda checagem após o catch
                        UUID.randomUUID(),
                        ID_VENDA,
                        MATRICULA,
                        10,
                        75,
                        null,
                        1L,
                        DATA_VENDA,
                        VALOR_VENDA,
                        new BigDecimal("0.1000"),
                        new BigDecimal("100.00"),
                        "INDIVIDUAL"
                )));

        when(resultadoCalculoRepository.findByMatriculaAndDataVendaAndRegraId(MATRICULA, DATA_VENDA, 1L))
                .thenReturn(Optional.empty());

        when(resultadoCalculoRepository.save(any(ResultadoCalculo.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key error"));

        // Execução
        CalculoComissaoResponse response = calculoService.calcularComissao(requestPadrao);

        // Deve recuperar e retornar o cálculo com sucesso
        assertNotNull(response);
        assertEquals(MATRICULA, response.matricula());
        assertEquals(VALOR_VENDA, response.valorOriginal());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());

        // Não deve tentar salvar log duplicado
        verify(logCalculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. Deve listar logs reais quando existirem registros")
    void deveListarLogsExistentes() {
        LogCalculoImutavel log = new LogCalculoImutavel(
                UUID.randomUUID(),
                ID_VENDA,
                MATRICULA,
                null,
                75,
                10,
                VALOR_VENDA,
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                1L,
                DATA_VENDA,
                "ECOMMERCE",
                "MOTOR_PRODUCAO"
        );

        when(logCalculoRepository.findAllByOrderByExecutadoEmDesc()).thenReturn(List.of(log));

        List<LogCalculoResponse> logs = calculoService.listarLogs();

        assertEquals(1, logs.size());
        assertEquals(MATRICULA, logs.get(0).matricula());
        assertEquals(new BigDecimal("100.00"), logs.get(0).valorComissao());
    }
}

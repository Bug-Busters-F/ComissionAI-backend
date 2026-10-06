package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.interpretador.proposta.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArtefatoExplicativoServiceTest {

    private ArtefatoExplicativoService service;

    @BeforeEach
    void setUp() {
        service = new ArtefatoExplicativoService();
    }

    @Test
    @DisplayName("1. Deve calcular taxa efetiva para DEFINIR_TAXA")
    void deveCalcularTaxaDefinirTaxa() {
        OperacaoBaseDTO op = OperacaoBaseDTO.definirTaxa(new BigDecimal("0.0500"));
        BigDecimal taxa = service.calcularTaxaEfetiva(op);
        assertEquals(new BigDecimal("0.0500"), taxa);
    }

    @Test
    @DisplayName("2. Deve calcular taxa efetiva para ACRESCIMO_PONTOS")
    void deveCalcularTaxaAcrescimoPontos() {
        ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", new BigDecimal("0.0300"), "Base");
        OperacaoBaseDTO op = new OperacaoBaseDTO(TipoOperacaoBase.ACRESCIMO_PONTOS, new BigDecimal("0.0150"), ref);
        BigDecimal taxa = service.calcularTaxaEfetiva(op);
        assertEquals(new BigDecimal("0.0450"), taxa);
    }

    @Test
    @DisplayName("3. Deve calcular taxa efetiva para DESCONTO_PONTOS")
    void deveCalcularTaxaDescontoPontos() {
        ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", new BigDecimal("0.0300"), "Base");
        OperacaoBaseDTO op = new OperacaoBaseDTO(TipoOperacaoBase.DESCONTO_PONTOS, new BigDecimal("0.0050"), ref);
        BigDecimal taxa = service.calcularTaxaEfetiva(op);
        assertEquals(new BigDecimal("0.0250"), taxa);
    }

    @Test
    @DisplayName("4. Deve calcular taxa efetiva para MULTIPLICADOR_BASE")
    void deveCalcularTaxaMultiplicadorBase() {
        ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", new BigDecimal("0.0300"), "Base");
        OperacaoBaseDTO op = new OperacaoBaseDTO(TipoOperacaoBase.MULTIPLICADOR_BASE, new BigDecimal("1.5000"), ref);
        BigDecimal taxa = service.calcularTaxaEfetiva(op);
        assertEquals(new BigDecimal("0.0450"), taxa);
    }

    @Test
    @DisplayName("5. Deve calcular taxa efetiva para DIVISOR_BASE")
    void deveCalcularTaxaDivisorBase() {
        ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", new BigDecimal("0.0300"), "Base");
        OperacaoBaseDTO op = new OperacaoBaseDTO(TipoOperacaoBase.DIVISOR_BASE, new BigDecimal("2.0000"), ref);
        BigDecimal taxa = service.calcularTaxaEfetiva(op);
        assertEquals(new BigDecimal("0.0150"), taxa);
    }

    @Test
    @DisplayName("6. Deve retornar null ao tentar DIVISOR_BASE com zero")
    void deveRetornarNullDivisaoPorZero() {
        ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", new BigDecimal("0.0300"), "Base");
        OperacaoBaseDTO op = new OperacaoBaseDTO(TipoOperacaoBase.DIVISOR_BASE, BigDecimal.ZERO, ref);
        BigDecimal taxa = service.calcularTaxaEfetiva(op);
        assertNull(taxa);
    }

    @Test
    @DisplayName("7. Validação: Deve gerar pendência impeditiva para faixa de valor incoerente")
    void deveGerarImpedimentoParaFaixaIncoerente() {
        FaixaValorDTO faixaIncoerente = new FaixaValorDTO(new BigDecimal("10000.00"), true, new BigDecimal("5000.00"), true);
        PropostaRegraDTO proposta = new PropostaRegraDTO(
                "bloco-1",
                "trecho",
                FiltrosRegraDTO.vazio(),
                faixaIncoerente,
                OperacaoBaseDTO.definirTaxa(new BigDecimal("0.0500")),
                new BigDecimal("0.0500"),
                null,
                null,
                null,
                null,
                null,
                null,
                PeriodoVigenciaDTO.de(LocalDate.now(), LocalDate.now().plusDays(30))
        );

        List<PendenciaPropostaDTO> pendencias = service.validarProposta(proposta);
        assertTrue(pendencias.stream().anyMatch(p -> "FAIXA_VALOR_INCOERENTE".equals(p.codigo())));
    }

    @Test
    @DisplayName("8. Validação: Deve gerar pendência impeditiva para data final anterior à inicial")
    void deveGerarImpedimentoParaDatasIncoerentes() {
        PeriodoVigenciaDTO vigenciaInvalida = PeriodoVigenciaDTO.de(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1));
        PropostaRegraDTO proposta = new PropostaRegraDTO(
                "bloco-1",
                "trecho",
                FiltrosRegraDTO.vazio(),
                FaixaValorDTO.semLimites(),
                OperacaoBaseDTO.definirTaxa(new BigDecimal("0.0500")),
                new BigDecimal("0.0500"),
                null,
                null,
                null,
                null,
                null,
                null,
                vigenciaInvalida
        );

        List<PendenciaPropostaDTO> pendencias = service.validarProposta(proposta);
        assertTrue(pendencias.stream().anyMatch(p -> "DATA_FIM_ANTERIOR_INICIO".equals(p.codigo())));
    }

    @Test
    @DisplayName("9. Artefatos: Deve gerar explicação XAI e Python equivalente válidos")
    void deveGerarExplicacaoEPythonEquivalente() {
        FiltrosRegraDTO filtros = new FiltrosRegraDTO("ECOMMERCE", 10, "PRETO", 75, null, null, null);
        FaixaValorDTO faixa = new FaixaValorDTO(new BigDecimal("5000.00"), false, null, true);
        OperacaoBaseDTO op = OperacaoBaseDTO.definirTaxa(new BigDecimal("0.0450"));
        PeriodoVigenciaDTO vig = PeriodoVigenciaDTO.de(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        PropostaRegraDTO proposta = new PropostaRegraDTO(
                "bloco-1",
                "trecho",
                filtros,
                faixa,
                op,
                new BigDecimal("0.0450"),
                null,
                null,
                null,
                null,
                null,
                null,
                vig
        );

        String explicacao = service.gerarExplicacao(proposta);
        assertNotNull(explicacao);
        assertTrue(explicacao.contains("4.50%"));
        assertTrue(explicacao.contains("ECOMMERCE"));
        assertTrue(explicacao.contains("PRETO"));
        assertTrue(explicacao.contains("5000.00"));

        String python = service.gerarPythonEquivalente(proposta);
        assertNotNull(python);
        assertTrue(python.contains("def calcular_comissao"));
        assertTrue(python.contains("ECOMMERCE"));
        assertTrue(python.contains("5000.0"));
        assertTrue(python.contains("0.0450"));
    }
}

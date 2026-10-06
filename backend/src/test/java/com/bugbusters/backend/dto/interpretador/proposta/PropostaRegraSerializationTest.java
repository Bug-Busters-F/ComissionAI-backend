package com.bugbusters.backend.dto.interpretador.proposta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PropostaRegraSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Deve serializar e desserializar PropostaRegraDTO com todos os campos novos")
    void deveSerializarEDesserializarPropostaRegraDTO() throws Exception {
        FiltrosRegraDTO filtros = new FiltrosRegraDTO("ECOMMERCE", 10, "PRETO", 75, 100, "VENDEDOR LOJA", "MATRIC-56");
        FaixaValorDTO condicaoValor = new FaixaValorDTO(new BigDecimal("5000.00"), false, new BigDecimal("15000.00"), true);
        ReferenciaBaseDTO refBase = new ReferenciaBaseDTO("BASE_COMISS", new BigDecimal("0.0300"), "Base Contratual Padrão");
        OperacaoBaseDTO operacao = new OperacaoBaseDTO(TipoOperacaoBase.ACRESCIMO_PONTOS, new BigDecimal("0.0150"), refBase);
        PeriodoVigenciaDTO vigencia = PeriodoVigenciaDTO.de(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        PropostaRegraDTO proposta = new PropostaRegraDTO(
                "bloco-1",
                "Comissão de taxa base + 1.5% no canal ecommerce para vendas acima de R$ 5.000",
                filtros,
                condicaoValor,
                operacao,
                new BigDecimal("0.0450"),
                List.of(new ReferenciaConsultadaDTO("MARCA", "cod_marca=10", "Marca PRETO")),
                Map.of("canal", OrigemCampo.TEXTO, "taxa", OrigemCampo.TEXTO),
                List.of(PendenciaPropostaDTO.aviso("filtros", "AVISO_TESTE", "Aviso de teste")),
                true,
                "Aplica taxa de 4.50%...",
                "def calcular_comissao(venda): ...",
                vigencia
        );

        String json = objectMapper.writeValueAsString(proposta);
        assertNotNull(json);
        assertTrue(json.contains("bloco-1"));
        assertTrue(json.contains("ACRESCIMO_PONTOS"));
        assertTrue(json.contains("5000.00"));

        PropostaRegraDTO deserializado = objectMapper.readValue(json, PropostaRegraDTO.class);
        assertEquals("bloco-1", deserializado.blocoId());
        assertEquals("ECOMMERCE", deserializado.filtros().canal());
        assertEquals(TipoOperacaoBase.ACRESCIMO_PONTOS, deserializado.operacaoBase().tipoOperacao());
        assertEquals(new BigDecimal("0.0450"), deserializado.taxaFinal());
        assertFalse(deserializado.condicaoValor().minInclusivo());
        assertTrue(deserializado.condicaoValor().maxInclusivo());
        assertEquals(LocalDate.of(2026, 10, 1), deserializado.vigencia().dataInicio());
    }

    @Test
    @DisplayName("Deve suportar todas as operações base incluindo DIVISOR_BASE")
    void deveSuportarOperacoesBaseIncluindoDivisor() throws Exception {
        for (TipoOperacaoBase tipo : TipoOperacaoBase.values()) {
            OperacaoBaseDTO op = new OperacaoBaseDTO(tipo, new BigDecimal("2.0000"), null);
            String json = objectMapper.writeValueAsString(op);
            OperacaoBaseDTO read = objectMapper.readValue(json, OperacaoBaseDTO.class);
            assertEquals(tipo, read.tipoOperacao());
        }
    }

    @Test
    @DisplayName("Deve suportar serialização de InterpretacaoMultiplaResponse com contadores e flags")
    void deveSerializarInterpretacaoMultiplaResponse() throws Exception {
        InterpretacaoMultiplaResponse response = new InterpretacaoMultiplaResponse(
                "Campanha Teste",
                List.of(),
                0,
                false,
                true,
                new BigDecimal("0.95")
        );

        String json = objectMapper.writeValueAsString(response);
        InterpretacaoMultiplaResponse read = objectMapper.readValue(json, InterpretacaoMultiplaResponse.class);
        assertEquals("Campanha Teste", read.tituloSugerido());
        assertEquals(0, read.quantidadeBlocos());
        assertFalse(read.possuiPendencias());
        assertTrue(read.todasCompletas());
    }
}

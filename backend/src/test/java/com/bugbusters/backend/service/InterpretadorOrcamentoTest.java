package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.interpretador.proposta.InterpretacaoMultiplaRequest;
import com.bugbusters.backend.dto.interpretador.proposta.InterpretacaoMultiplaResponse;
import com.bugbusters.backend.model.OrigemOrcamentoCampanha;
import com.bugbusters.backend.service.client.AiServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class InterpretadorOrcamentoTest {

    @Autowired
    private InterpretadorService interpretadorService;

    @MockitoBean
    private AiServiceClient aiServiceClient;

    @Test
    @DisplayName("Deve extrair orçamento a partir de texto com valor em reais e casas decimais")
    void deveExtrairOrcamentoFormatadoEmReais() {
        String texto = "Campanha Especial de Black Friday com orçamento de R$ 75.000,00 para vendedores elegíveis.";
        when(aiServiceClient.chamarServicoPythonV2(any())).thenReturn(
                Map.of("propostas", List.of())
        );

        InterpretacaoMultiplaResponse res = interpretadorService.processarInterpretacaoMultipla(
                new InterpretacaoMultiplaRequest(texto, Map.of())
        );

        assertThat(res.orcamentoExtraido()).isNotNull();
        assertThat(res.orcamentoExtraido().valor()).isEqualByComparingTo(new BigDecimal("75000.00"));
        assertThat(res.orcamentoExtraido().origem()).isEqualTo(OrigemOrcamentoCampanha.EXTRAIDO_IA);
    }

    @Test
    @DisplayName("Deve extrair orçamento com multiplicador 'mil'")
    void deveExtrairOrcamentoComMultiplicadorMil() {
        String texto = "Nova regra com verba de 250 mil reais para a equipe de vendas de calçados.";
        when(aiServiceClient.chamarServicoPythonV2(any())).thenReturn(
                Map.of("propostas", List.of())
        );

        InterpretacaoMultiplaResponse res = interpretadorService.processarInterpretacaoMultipla(
                new InterpretacaoMultiplaRequest(texto, Map.of())
        );

        assertThat(res.orcamentoExtraido()).isNotNull();
        assertThat(res.orcamentoExtraido().valor()).isEqualByComparingTo(new BigDecimal("250000"));
        assertThat(res.orcamentoExtraido().origem()).isEqualTo(OrigemOrcamentoCampanha.EXTRAIDO_IA);
    }

    @Test
    @DisplayName("Deve retornar null quando o texto não contiver orçamento monetário")
    void deveRetornarNullQuandoNaoHouverOrcamentoNoTexto() {
        String texto = "Comissão de 3% sobre vendas de eletrônicos para o mês de outubro.";
        when(aiServiceClient.chamarServicoPythonV2(any())).thenReturn(
                Map.of("propostas", List.of())
        );

        InterpretacaoMultiplaResponse res = interpretadorService.processarInterpretacaoMultipla(
                new InterpretacaoMultiplaRequest(texto, Map.of())
        );

        assertThat(res.orcamentoExtraido()).isNull();
    }
}

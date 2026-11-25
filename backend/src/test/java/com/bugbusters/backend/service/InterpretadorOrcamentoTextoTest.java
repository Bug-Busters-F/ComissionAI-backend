package com.bugbusters.backend.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretadorOrcamentoTextoTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Em dezembro de 2025 todos da marca VERMELHO recebem 5% de comissão. O orçamento total para a campanha é R$130000.|130000",
            "O orçamento da campanha é de R$ 130.000,00.|130000.00",
            "Orçamento de R$130000|130000",
            "Orçamento: R$130000,50|130000.50",
            "Orçamento total para campanha será de R$ 130.000,00.|130000.00",
            "ORÇAMENTO TOTAL PARA A CAMPANHA É R$130000|130000",
            "Orcamento previsto em BRL 130000|130000",
            "Teto orçamentário: R$ 750.000,00|750000.00",
            "Verba de 250 mil reais|250000",
            "Orçamento de 1 milhão de reais|1000000",
            "Orçamento de 1,5 milhões de reais|1500000.0",
            "Orçamento de 13000000|13000000",
            "Orçamento de R$ 130.000.000,99|130000000.99"
    })
    void extraiValorCompletoDaProposta(String texto, String valor) {
        var result = InterpretadorService.extrairOrcamento(Map.of(), texto);
        assertThat(result).isNotNull();
        assertThat(result.valor()).isEqualByComparingTo(new BigDecimal(valor));
        assertThat(texto).contains(result.trechoOriginal());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Em dezembro de 2025, a comissão é de 5% sobre vendas de R$130000.",
            "Orçamento de R$ 0,00", "Orçamento de R$ -130000", "Orçamento de R$130000.50"
    })
    void naoInventaOrcamentoNemAceitaPrefixoDeValorInvalido(String texto) {
        assertThat(InterpretadorService.extrairOrcamento(Map.of(), texto)).isNull();
    }
}

package com.bugbusters.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bugbusters.backend.service.TaxaComissaoResolver;

class RegraFaixaTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    @Test
    @DisplayName("Sem faixa aceita qualquer valor; limites respeitam inclusivo/exclusivo")
    void deveAvaliarFaixa() {
        Regra semFaixa = new Regra();
        assertFalse(semFaixa.possuiFaixa());
        assertTrue(semFaixa.aceitaValor(v("0.01")));

        Regra faixa = new Regra();
        faixa.setValorMin(v("100.00"));
        faixa.setValorMax(v("200.00"));
        assertTrue(faixa.possuiFaixa());
        assertTrue(faixa.aceitaValor(v("100.00")));
        assertTrue(faixa.aceitaValor(v("200.00")));
        assertFalse(faixa.aceitaValor(v("99.99")));
        assertFalse(faixa.aceitaValor(v("200.01")));

        faixa.setMinInclusivo(false);
        faixa.setMaxInclusivo(false);
        assertFalse(faixa.aceitaValor(v("100.00")));
        assertFalse(faixa.aceitaValor(v("200.00")));
        assertTrue(faixa.aceitaValor(v("100.01")));
        assertTrue(faixa.aceitaValor(v("199.99")));

        assertFalse(faixa.aceitaValor(null));
    }

    @Test
    @DisplayName("Pesos de especificidade documentados: matrícula 16, loja 8, cargo 4, canal 2, marca 1")
    void devePesarEspecificidade() {
        assertEquals(0, TaxaComissaoResolver.especificidade(null, null, null, null, null));
        assertEquals(31, TaxaComissaoResolver.especificidade(10, 75, 100, "M1", "ECOMMERCE"));
        assertEquals(0, TaxaComissaoResolver.especificidade(null, null, null, " ", "PADRAO"));
        assertEquals(8 + 1, TaxaComissaoResolver.especificidade(10, 75, null, null, null));
    }
}

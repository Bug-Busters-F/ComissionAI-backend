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
    @DisplayName("Sem limites não há faixa; limite de inclusividade nulo conta como inclusivo")
    void deveAvaliarFaixa() {
        Regra semFaixa = new Regra();
        assertFalse(semFaixa.possuiFaixa());

        Regra faixa = new Regra();
        faixa.setValorMaximo(v("200.00"));
        assertTrue(faixa.possuiFaixa());
        assertTrue(faixa.minEhInclusivo());
        assertTrue(faixa.maxEhInclusivo());

        faixa.setMinInclusivo(false);
        faixa.setMaxInclusivo(false);
        assertFalse(faixa.minEhInclusivo());
        assertFalse(faixa.maxEhInclusivo());
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

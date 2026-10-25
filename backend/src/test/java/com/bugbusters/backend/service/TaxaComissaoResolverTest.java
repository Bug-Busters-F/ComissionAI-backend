package com.bugbusters.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.service.CoberturaBaseService;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.store.Store;

@ExtendWith(MockitoExtension.class)
class TaxaComissaoResolverTest {

    @Mock
    private CoberturaBaseService coberturaBaseService;

    @Mock
    private RegraRepository regraRepository;

    @InjectMocks
    private TaxaComissaoResolver resolver;

    private Sale sale;
    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;

    @BeforeEach
    void setUp() {
        brand = new Brand();
        brand.setId(UUID.randomUUID());
        brand.setCode(10);
        brand.setDescription("PRETO");

        store = new Store();
        store.setId(UUID.randomUUID());
        store.setCode(75);
        store.setDescription("LOJA 75");

        position = new Position();
        position.setId(UUID.randomUUID());
        position.setCode(100);
        position.setDescription("VENDEDOR LOJA");

        registration = new Registration();
        registration.setId(UUID.randomUUID());
        registration.setRegistration("MATRIC-001");
        registration.setStore(store);
        registration.setPosition(position);
        registration.setAdmissDate(LocalDate.of(2025, 1, 1));
        registration.setDemissDate(null);

        sale = new Sale();
        sale.setId(UUID.randomUUID());
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setValue(new BigDecimal("1000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setSaleChannel("ECOMMERCE");
    }

    @Test
    @DisplayName("1. Prioridade 1: Deve selecionar regra ativa de campanha prioritariamente, sobrepondo taxa base de tb_basecomiss")
    void deveSelecionarRegraAtivaPrioritariamente() {
        Regra regra = new Regra(
                null,
                "Regra Campanha Especial",
                "ECOMMERCE",
                brand.getCode(),
                store.getCode(),
                position.getCode(),
                position.getDescription(),
                registration.getRegistration(),
                new BigDecimal("0.0500"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
        regra.setId(99L);

        when(regraRepository.findRegrasAplicaveis(
                eq(sale.getSaleDate()),
                eq(brand.getCode()),
                eq(store.getCode()),
                eq(position.getCode()),
                eq(registration.getRegistration()),
                eq("ECOMMERCE"),
                eq(StatusRegra.ATIVA)
        )).thenReturn(List.of(regra));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.0500"), result.taxa());
        assertEquals(99L, result.idRegra());
        assertEquals("REGRA_NEGOCIO", result.origemTaxa());
    }

    @Test
    @DisplayName("2. Prioridade 2: Deve selecionar taxa de tb_basecomiss como fallback quando regra ativa não existir")
    void deveSelecionarTaxaDeBaseComissComoFallback() {
        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(coberturaBaseService.obterTaxaBase(
                eq(brand.getCode()), eq(position.getCode()), any()
        )).thenReturn(Optional.of(new BigDecimal("0.1000")));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.1000"), result.taxa());
        assertEquals("BASE_COMISS", result.origemTaxa());
    }

    @Test
    @DisplayName("2.1. Deve ignorar a regra técnica padrão e usar a taxa base")
    void deveIgnorarRegraPadraoTecnicaAoResolverTaxa() {
        Regra regraPadraoTecnica = new Regra(
                null,
                "Regra Geral Padrão",
                "PADRAO",
                new BigDecimal("0.0250"),
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2035, 12, 31)
        );
        regraPadraoTecnica.setId(1L);

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regraPadraoTecnica));

        when(coberturaBaseService.obterTaxaBase(
                eq(brand.getCode()), eq(position.getCode()), any()
        )).thenReturn(Optional.of(new BigDecimal("0.0250")));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.0250"), result.taxa());
        assertEquals(1L, result.idRegra());
        assertEquals("BASE_COMISS", result.origemTaxa());
    }

    @Test
    @DisplayName("3. Impedimento: Deve retornar impedimento quando nem regra nem basecomiss existirem")
    void deveRetornarImpedimentoQuandoTaxaNaoEncontrada() {
        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.empty());

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertNotNull(result.motivoImpedimento());
        assertTrue(result.motivoImpedimento().contains("Taxa de comissão não encontrada"));
    }

    @Test
    @DisplayName("4. Impedimento: Colaborador sem cargo deve gerar impedimento")
    void deveRetornarImpedimentoQuandoSemCargo() {
        registration.setPosition(null);

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertTrue(result.motivoImpedimento().contains("sem cargo vinculado"));
    }

    @Test
    @DisplayName("5. Impedimento: Venda antes da data de admissão do colaborador deve gerar impedimento")
    void deveRetornarImpedimentoQuandoVendaAntesAdmissao() {
        registration.setAdmissDate(LocalDate.of(2026, 10, 1)); // Admissão em outubro
        sale.setSaleDate(LocalDate.of(2026, 9, 15));          // Venda em setembro

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertTrue(result.motivoImpedimento().contains("anterior à admissão"));
    }

    @Test
    @DisplayName("6. Impedimento: Venda após data de demissão do colaborador deve gerar impedimento")
    void deveRetornarImpedimentoQuandoVendaAposDemissao() {
        registration.setDemissDate(LocalDate.of(2026, 9, 1)); // Demissão em 01/09
        sale.setSaleDate(LocalDate.of(2026, 9, 15));         // Venda em 15/09

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertTrue(result.motivoImpedimento().contains("posterior à demissão"));
    }

    @Test
    @DisplayName("7. Precedência Determinística: Regra mais específica deve prevalecer sobre regra genérica")
    void deveSelecionarRegraMaisEspecificaQuandoHouverMultiplasRegrasCompativeis() {
        Regra regraGeralMarca = new Regra(
                null,
                "Regra Geral Marca",
                null,
                brand.getCode(),
                null,
                null,
                null,
                null,
                new BigDecimal("0.0300"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
        regraGeralMarca.setId(10L);

        Regra regraEspecificaLoja = new Regra(
                null,
                "Regra Específica Loja",
                "ECOMMERCE",
                brand.getCode(),
                store.getCode(),
                null,
                null,
                null,
                new BigDecimal("0.0700"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
        regraEspecificaLoja.setId(20L);

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regraGeralMarca, regraEspecificaLoja));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.0700"), result.taxa());
        assertEquals(20L, result.idRegra());
        assertEquals("REGRA_NEGOCIO", result.origemTaxa());
    }

    @Test
    @DisplayName("8. Bloqueio de Conflito: Múltiplas regras no mesmo nível de especificidade devem ser bloqueadas")
    void deveBloquearCalculoQuandoHouverMultiplasRegrasConflitantesNoMesmoNivel() {
        Regra regra1 = new Regra(
                null,
                "Regra Campanha A",
                "ECOMMERCE",
                brand.getCode(),
                store.getCode(),
                null,
                null,
                null,
                new BigDecimal("0.0500"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
        regra1.setId(101L);

        Regra regra2 = new Regra(
                null,
                "Regra Campanha B",
                "ECOMMERCE",
                brand.getCode(),
                store.getCode(),
                null,
                null,
                null,
                new BigDecimal("0.0800"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
        regra2.setId(102L);

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra1, regra2));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertNotNull(result.motivoImpedimento());
        assertTrue(result.motivoImpedimento().contains("Conflito de regras"));
        assertTrue(result.motivoImpedimento().contains("ambiguidade"));
        assertTrue(result.motivoImpedimento().contains("101"));
        assertTrue(result.motivoImpedimento().contains("102"));
    }

    @Test
    @DisplayName("9. Impedimento: Venda com valor zero ou negativo deve gerar impedimento")
    void deveRetornarImpedimentoQuandoValorInvalido() {
        sale.setValue(BigDecimal.ZERO);

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertTrue(result.motivoImpedimento().contains("Valor da venda inválido"));
    }
    @Test
    void deveExcluirRegraTecnicaAntesDeVerificarEmpate() {
        Regra tecnica = new Regra(null, "Regra Geral Padrão", "PADRAO",
                new BigDecimal("0.1000"), LocalDate.of(2020, 1, 1), LocalDate.of(2035, 12, 31));
        tecnica.setId(1L);
        Regra campanha = new Regra(null, "Campanha geral", "PADRAO",
                new BigDecimal("0.0300"), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        campanha.setId(42L);
        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(tecnica, campanha));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(42L, result.idRegra());
        assertEquals(new BigDecimal("0.0300"), result.taxa());
        assertEquals("REGRA_NEGOCIO", result.origemTaxa());
        assertEquals("REGRA#42 - Campanha geral", result.versaoOuReferenciaRegra());
    }

    // =========================================================================
    // Testes de Faixas de Valor (Inclusivo e Exclusivo) & Casos de Fronteira (BACK-4)
    // =========================================================================

    @Test
    @DisplayName("10. Faixas de Valor: Deve aplicar regra com limite inferior inclusivo no valor de fronteira exato")
    void deveAplicarRegraComLimiteInferiorInclusivo() {
        Regra regra = new Regra(null, "Regra Min Inclusivo", "ECOMMERCE", new BigDecimal("0.0500"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(50L);
        regra.setValorMinimo(new BigDecimal("1000.00"));
        regra.setMinInclusivo(true);

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        // Venda exatamente no valor mínimo
        sale.setValue(new BigDecimal("1000.00"));
        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(50L, result.idRegra());
        assertEquals(new BigDecimal("0.0500"), result.taxa());
    }

    @Test
    @DisplayName("11. Faixas de Valor: Deve descartar regra e fazer fallback para BaseComiss quando valor igual a limite inferior exclusivo")
    void deveDescartarRegraQuandoLimiteInferiorExclusivo() {
        Regra regra = new Regra(null, "Regra Min Exclusivo", "ECOMMERCE", new BigDecimal("0.0500"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(51L);
        regra.setValorMinimo(new BigDecimal("1000.00"));
        regra.setMinInclusivo(false); // Estritamente maior que 1000.00

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.0200")));

        // Venda exatamente no valor mínimo (não atende exclusivo)
        sale.setValue(new BigDecimal("1000.00"));
        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals("BASE_COMISS", result.origemTaxa());
        assertEquals(new BigDecimal("0.0200"), result.taxa());

        // Venda com valor ligeiramente superior (atende exclusivo)
        sale.setValue(new BigDecimal("1000.01"));
        ResolucaoTaxaResult resultAtende = resolver.resolverTaxa(sale);

        assertTrue(resultAtende.sucesso());
        assertEquals(51L, resultAtende.idRegra());
        assertEquals(new BigDecimal("0.0500"), resultAtende.taxa());
    }

    @Test
    @DisplayName("12. Faixas de Valor: Deve aplicar regra com limite superior inclusivo no valor de fronteira exato")
    void deveAplicarRegraComLimiteSuperiorInclusivo() {
        Regra regra = new Regra(null, "Regra Max Inclusivo", "ECOMMERCE", new BigDecimal("0.0600"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(52L);
        regra.setValorMaximo(new BigDecimal("5000.00"));
        regra.setMaxInclusivo(true);

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        // Venda exatamente no teto máximo
        sale.setValue(new BigDecimal("5000.00"));
        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(52L, result.idRegra());
        assertEquals(new BigDecimal("0.0600"), result.taxa());
    }

    @Test
    @DisplayName("13. Faixas de Valor: Deve descartar regra quando valor igual a limite superior exclusivo")
    void deveDescartarRegraQuandoLimiteSuperiorExclusivo() {
        Regra regra = new Regra(null, "Regra Max Exclusivo", "ECOMMERCE", new BigDecimal("0.0600"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(53L);
        regra.setValorMaximo(new BigDecimal("5000.00"));
        regra.setMaxInclusivo(false); // Estritamente menor que 5000.00

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.0200")));

        // Venda exatamente no valor máximo (não atende exclusivo)
        sale.setValue(new BigDecimal("5000.00"));
        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals("BASE_COMISS", result.origemTaxa());

        // Venda ligeiramente inferior (atende exclusivo)
        sale.setValue(new BigDecimal("4999.99"));
        ResolucaoTaxaResult resultAtende = resolver.resolverTaxa(sale);

        assertTrue(resultAtende.sucesso());
        assertEquals(53L, resultAtende.idRegra());
        assertEquals(new BigDecimal("0.0600"), resultAtende.taxa());
    }

    @Test
    @DisplayName("14. Faixas Contíguas: Deve aplicar filtros antes e evitar falso empate em faixas contíguas na fronteira")
    void deveEvitarEmpateEmFaixasContiguasNaFronteira() {
        // Faixa 1: até R$ 5.000 (inclusivo)
        Regra faixa1 = new Regra(null, "Faixa 1 Ate 5k", "ECOMMERCE", new BigDecimal("0.0300"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        faixa1.setId(61L);
        faixa1.setValorMaximo(new BigDecimal("5000.00"));
        faixa1.setMaxInclusivo(true);

        // Faixa 2: acima de R$ 5.000 (exclusivo)
        Regra faixa2 = new Regra(null, "Faixa 2 Acima 5k", "ECOMMERCE", new BigDecimal("0.0700"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        faixa2.setId(62L);
        faixa2.setValorMinimo(new BigDecimal("5000.00"));
        faixa2.setMinInclusivo(false);

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(faixa1, faixa2));

        // Venda de exatamente R$ 5.000,00 deve selecionar Faixa 1 sem ambiguidade
        sale.setValue(new BigDecimal("5000.00"));
        ResolucaoTaxaResult result1 = resolver.resolverTaxa(sale);

        assertTrue(result1.sucesso());
        assertEquals(61L, result1.idRegra());
        assertEquals(new BigDecimal("0.0300"), result1.taxa());

        // Venda de R$ 5.000,01 deve selecionar Faixa 2 sem ambiguidade
        sale.setValue(new BigDecimal("5000.01"));
        ResolucaoTaxaResult result2 = resolver.resolverTaxa(sale);

        assertTrue(result2.sucesso());
        assertEquals(62L, result2.idRegra());
        assertEquals(new BigDecimal("0.0700"), result2.taxa());
    }

    // =========================================================================
    // Testes de Ajustes sobre a Base Contratual (BACK-4)
    // =========================================================================

    @Test
    @DisplayName("15. Ajustes: Deve calcular ACRESCIMO_PONTOS sobre a taxa base contratual")
    void deveCalcularAcrescimoPontosSobreTaxaBase() {
        Regra regra = new Regra(null, "Regra Bonus", "ECOMMERCE", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(71L);
        regra.setTipoOperacao(TipoOperacaoBase.ACRESCIMO_PONTOS);
        regra.setValorAjuste(new BigDecimal("0.0150")); // + 1.5%

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.0300")));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.0450"), result.taxa()); // 3% + 1.5% = 4.5%
        assertEquals(71L, result.idRegra());
        assertEquals("REGRA_NEGOCIO", result.origemTaxa());
        assertTrue(result.versaoOuReferenciaRegra().contains("ACRESCIMO_PONTOS"));
    }

    @Test
    @DisplayName("16. Ajustes: Deve calcular DESCONTO_PONTOS sobre a taxa base contratual")
    void deveCalcularDescontoPontosSobreTaxaBase() {
        Regra regra = new Regra(null, "Regra Reducao", "ECOMMERCE", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(72L);
        regra.setTipoOperacao(TipoOperacaoBase.DESCONTO_PONTOS);
        regra.setValorAjuste(new BigDecimal("0.0050")); // - 0.5%

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.0300")));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.0250"), result.taxa()); // 3% - 0.5% = 2.5%
        assertEquals(72L, result.idRegra());
    }

    @Test
    @DisplayName("17. Ajustes: Deve calcular MULTIPLICADOR_BASE sobre a taxa base contratual")
    void deveCalcularMultiplicadorBase() {
        Regra regra = new Regra(null, "Regra Multiplicador", "ECOMMERCE", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(73L);
        regra.setTipoOperacao(TipoOperacaoBase.MULTIPLICADOR_BASE);
        regra.setValorAjuste(new BigDecimal("1.5000")); // 1.5x a base

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.0300")));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.0450"), result.taxa()); // 3% * 1.5 = 4.5%
        assertEquals(73L, result.idRegra());
    }

    @Test
    @DisplayName("18. Ajustes: Deve calcular DIVISOR_BASE sobre a taxa base contratual")
    void deveCalcularDivisorBase() {
        Regra regra = new Regra(null, "Regra Divisor", "ECOMMERCE", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(74L);
        regra.setTipoOperacao(TipoOperacaoBase.DIVISOR_BASE);
        regra.setValorAjuste(new BigDecimal("2.0000")); // Metade da base

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.0300")));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.0150"), result.taxa()); // 3% / 2 = 1.5%
        assertEquals(74L, result.idRegra());
    }

    @Test
    @DisplayName("19. Impedimento de Ajuste: Falta de taxa em tb_basecomiss para regra que exige ajuste deve gerar impedimento")
    void deveGerarImpedimentoQuandoBaseNaoEncontradaParaAjuste() {
        Regra regra = new Regra(null, "Regra Exige Base", "ECOMMERCE", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(75L);
        regra.setTipoOperacao(TipoOperacaoBase.ACRESCIMO_PONTOS);
        regra.setValorAjuste(new BigDecimal("0.0100"));

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.empty());

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertTrue(result.motivoImpedimento().contains("requer taxa base contratual"));
        assertTrue(result.motivoImpedimento().contains("75"));
    }

    @Test
    @DisplayName("20. Impedimento de Ajuste: Divisão por zero em DIVISOR_BASE deve gerar impedimento")
    void deveGerarImpedimentoEmDivisaoPorZero() {
        Regra regra = new Regra(null, "Regra Div Zero", "ECOMMERCE", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(76L);
        regra.setTipoOperacao(TipoOperacaoBase.DIVISOR_BASE);
        regra.setValorAjuste(BigDecimal.ZERO);

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.0300")));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertTrue(result.motivoImpedimento().contains("divisão por zero"));
    }

    @Test
    @DisplayName("21. Impedimento de Ajuste: Ajuste com taxa resultante menor ou igual a zero deve gerar impedimento")
    void deveGerarImpedimentoQuandoTaxaResultanteMenorOuIgualZero() {
        Regra regra = new Regra(null, "Regra Desconto Excessivo", "ECOMMERCE", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setId(77L);
        regra.setTipoOperacao(TipoOperacaoBase.DESCONTO_PONTOS);
        regra.setValorAjuste(new BigDecimal("0.0500")); // Base 3% - 5% = -2%

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regra));

        when(coberturaBaseService.obterTaxaBase(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.0300")));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertFalse(result.sucesso());
        assertTrue(result.motivoImpedimento().contains("taxa percentual inválida"));
    }

    @Test
    @DisplayName("22. Regras Individuais: Matrícula com peso 16 deve sobrepor regra geral de loja com peso 8")
    void devePriorizarRegraIndividualDeMatriculaSobreRegraDeLoja() {
        Regra regraLoja = new Regra(null, "Regra Loja Geral", "ECOMMERCE", brand.getCode(),
                store.getCode(), null, null, null, new BigDecimal("0.0400"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regraLoja.setId(81L); // Loja (8) + Canal (2) + Marca (1) = 11

        Regra regraMatricula = new Regra(null, "Regra Vendedor Individual", null, null,
                null, null, null, registration.getRegistration(), new BigDecimal("0.0800"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regraMatricula.setId(82L); // Matrícula (16) = 16

        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(regraLoja, regraMatricula));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(82L, result.idRegra()); // Matrícula vence
        assertEquals(new BigDecimal("0.0800"), result.taxa());
        assertEquals("REGRA_NEGOCIO", result.origemTaxa());
    }
}

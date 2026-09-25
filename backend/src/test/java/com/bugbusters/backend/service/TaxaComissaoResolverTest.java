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
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
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
    private BaseComissRepository baseComissRepository;

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

        BaseComiss baseComiss = new BaseComiss(brand, position, new BigDecimal("0.1000"));
        baseComiss.setReferenceMonth(LocalDate.of(2026, 9, 1));

        when(baseComissRepository.findFirstByBrandIdAndPositionIdAndReferenceMonth(
                eq(brand.getId()), eq(position.getId()), eq(LocalDate.of(2026, 9, 1))
        )).thenReturn(Optional.of(baseComiss));

        ResolucaoTaxaResult result = resolver.resolverTaxa(sale);

        assertTrue(result.sucesso());
        assertEquals(new BigDecimal("0.1000"), result.taxa());
        assertEquals("BASE_COMISS", result.origemTaxa());
    }

    @Test
    @DisplayName("3. Impedimento: Deve retornar impedimento quando nem regra nem basecomiss existirem")
    void deveRetornarImpedimentoQuandoTaxaNaoEncontrada() {
        when(regraRepository.findRegrasAplicaveis(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(baseComissRepository.findFirstByBrandIdAndPositionIdAndReferenceMonth(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(baseComissRepository.findFirstByBrandIdAndPositionIdOrderByReferenceMonthDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(any(), any()))
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
}

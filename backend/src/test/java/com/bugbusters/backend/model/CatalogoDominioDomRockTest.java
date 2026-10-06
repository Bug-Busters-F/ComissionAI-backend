package com.bugbusters.backend.model;

import com.bugbusters.backend.dto.interpretador.CatalogoDominioResponseDTO;
import com.bugbusters.backend.dto.interpretador.proposta.OperacaoBaseDTO;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.service.InterpretadorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoDominioDomRockTest {

    @Test
    @DisplayName("1. Deve conter as 6 marcas conhecidas oficiais do cliente")
    void deveConterMarcasConhecidas() {
        assertEquals(6, CatalogoDominioDomRock.MARCAS.size());
        assertEquals("PRETO", CatalogoDominioDomRock.MARCAS.get(10));
        assertEquals("BRANCO", CatalogoDominioDomRock.MARCAS.get(20));
        assertEquals("AZUL", CatalogoDominioDomRock.MARCAS.get(30));
        assertEquals("VERMELHO", CatalogoDominioDomRock.MARCAS.get(40));
        assertEquals("AMARELO", CatalogoDominioDomRock.MARCAS.get(50));
        assertEquals("CINZA", CatalogoDominioDomRock.MARCAS.get(60));
    }

    @Test
    @DisplayName("2. Deve mapear os 5 cargos oficiais e seus canais associados")
    void deveMapearCargosOficiais() {
        assertEquals(5, Cargo.values().length);

        Optional<Cargo> vendedorLoja = Cargo.buscarPorDescricao("VENDEDOR LOJA");
        assertTrue(vendedorLoja.isPresent());
        assertEquals(100, vendedorLoja.get().getCodigo());
        assertEquals("LOJA_FISICA", vendedorLoja.get().getCanalPadrao());

        Optional<Cargo> gerenteLoja = Cargo.buscarPorDescricao("GERENTE DE LOJA");
        assertTrue(gerenteLoja.isPresent());
        assertEquals(150, gerenteLoja.get().getCodigo());
        assertEquals("LOJA_FISICA", gerenteLoja.get().getCanalPadrao());

        Optional<Cargo> gerenteQuiosque = Cargo.buscarPorDescricao("GERENTE QUIOSQUE");
        assertTrue(gerenteQuiosque.isPresent());
        assertEquals(150, gerenteQuiosque.get().getCodigo());
        assertEquals("QUIOSQUE", gerenteQuiosque.get().getCanalPadrao());

        Optional<Cargo> vendedorBalcao = Cargo.buscarPorDescricao("VENDEDOR BALCAO");
        assertTrue(vendedorBalcao.isPresent());
        assertEquals(200, vendedorBalcao.get().getCodigo());
        assertEquals("BALCAO", vendedorBalcao.get().getCanalPadrao());

        Optional<Cargo> assistenteVendas = Cargo.buscarPorDescricao("ASSISTENTE DE VENDAS");
        assertTrue(assistenteVendas.isPresent());
        assertEquals(300, assistenteVendas.get().getCodigo());
        assertEquals("LOJA_FISICA", assistenteVendas.get().getCanalPadrao());

        // Cargo 150 deve retornar as duas funções
        List<Cargo> cargos150 = Cargo.buscarPorCodigo(150);
        assertEquals(2, cargos150.size());
        assertTrue(cargos150.contains(Cargo.GERENTE_DE_LOJA));
        assertTrue(cargos150.contains(Cargo.GERENTE_QUIOSQUE));
    }

    @Test
    @DisplayName("3. Deve validar e formatar as 80 lojas oficiais da rede")
    void deveValidarEFormatarLojas() {
        assertEquals(80, CatalogoDominioDomRock.TOTAL_LOJAS);
        assertEquals(80, CatalogoDominioDomRock.LOJAS.size());

        assertTrue(CatalogoDominioDomRock.isLojaValida(1));
        assertTrue(CatalogoDominioDomRock.isLojaValida(80));
        assertTrue(CatalogoDominioDomRock.isLojaValida(42));
        assertFalse(CatalogoDominioDomRock.isLojaValida(0));
        assertFalse(CatalogoDominioDomRock.isLojaValida(-5));
        assertFalse(CatalogoDominioDomRock.isLojaValida(81));
        assertFalse(CatalogoDominioDomRock.isLojaValida(100));

        assertEquals("LOJA-1", CatalogoDominioDomRock.formatarLoja(1));
        assertEquals("LOJA-80", CatalogoDominioDomRock.formatarLoja(80));

        // Extração por regex
        assertEquals(Optional.of(12), CatalogoDominioDomRock.extrairCodigoLoja("Campanha para a loja 12 em outubro"));
        assertEquals(Optional.of(45), CatalogoDominioDomRock.extrairCodigoLoja("Ajuste na LOJA-45"));
        assertEquals(Optional.empty(), CatalogoDominioDomRock.extrairCodigoLoja("Sem loja especificada"));
    }

    @Test
    @DisplayName("4. Deve validar e extrair matrículas no padrão MATRIC-<id>")
    void deveValidarEExtrairMatriculas() {
        assertTrue(CatalogoDominioDomRock.isMatriculaValida("MATRIC-1"));
        assertTrue(CatalogoDominioDomRock.isMatriculaValida("MATRIC-186"));
        assertTrue(CatalogoDominioDomRock.isMatriculaValida("MATRIC-589"));
        assertFalse(CatalogoDominioDomRock.isMatriculaValida("186"));
        assertFalse(CatalogoDominioDomRock.isMatriculaValida("FUNC-123"));
        assertFalse(CatalogoDominioDomRock.isMatriculaValida(""));

        assertEquals(Optional.of("MATRIC-186"), CatalogoDominioDomRock.extrairMatricula("Bonificação para o vendedor MATRIC-186"));
        assertEquals(Optional.of("MATRIC-56"), CatalogoDominioDomRock.extrairMatricula("matric-56 elegível"));
        assertEquals(Optional.empty(), CatalogoDominioDomRock.extrairMatricula("Sem vendedor específico"));
    }

    @Test
    @DisplayName("5. Deve conter as 30 taxas contratuais padrão exatas de BASE_COMMISS_FINAL.xlsx")
    void deveConterTaxasContratuaisExatasDoExcel() {
        // PRETO (10)
        assertEquals(new BigDecimal("0.0250"), CatalogoDominioDomRock.obterTaxaBaseContratual(10, "VENDEDOR LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0100"), CatalogoDominioDomRock.obterTaxaBaseContratual(10, "GERENTE DE LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0075"), CatalogoDominioDomRock.obterTaxaBaseContratual(10, "GERENTE QUIOSQUE").orElseThrow());
        assertEquals(new BigDecimal("0.0200"), CatalogoDominioDomRock.obterTaxaBaseContratual(10, "VENDEDOR BALCAO").orElseThrow());
        assertEquals(new BigDecimal("0.0150"), CatalogoDominioDomRock.obterTaxaBaseContratual(10, "ASSISTENTE DE VENDAS").orElseThrow());

        // BRANCO (20)
        assertEquals(new BigDecimal("0.0300"), CatalogoDominioDomRock.obterTaxaBaseContratual(20, "VENDEDOR LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0150"), CatalogoDominioDomRock.obterTaxaBaseContratual(20, "GERENTE DE LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0125"), CatalogoDominioDomRock.obterTaxaBaseContratual(20, "GERENTE QUIOSQUE").orElseThrow());
        assertEquals(new BigDecimal("0.0250"), CatalogoDominioDomRock.obterTaxaBaseContratual(20, "VENDEDOR BALCAO").orElseThrow());
        assertEquals(new BigDecimal("0.0200"), CatalogoDominioDomRock.obterTaxaBaseContratual(20, "ASSISTENTE DE VENDAS").orElseThrow());

        // AZUL (30)
        assertEquals(new BigDecimal("0.0200"), CatalogoDominioDomRock.obterTaxaBaseContratual(30, "VENDEDOR LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0050"), CatalogoDominioDomRock.obterTaxaBaseContratual(30, "GERENTE DE LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0025"), CatalogoDominioDomRock.obterTaxaBaseContratual(30, "GERENTE QUIOSQUE").orElseThrow());
        assertEquals(new BigDecimal("0.0150"), CatalogoDominioDomRock.obterTaxaBaseContratual(30, "VENDEDOR BALCAO").orElseThrow());
        assertEquals(new BigDecimal("0.0100"), CatalogoDominioDomRock.obterTaxaBaseContratual(30, "ASSISTENTE DE VENDAS").orElseThrow());

        // VERMELHO (40)
        assertEquals(new BigDecimal("0.0350"), CatalogoDominioDomRock.obterTaxaBaseContratual(40, "VENDEDOR LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0200"), CatalogoDominioDomRock.obterTaxaBaseContratual(40, "GERENTE DE LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0175"), CatalogoDominioDomRock.obterTaxaBaseContratual(40, "GERENTE QUIOSQUE").orElseThrow());
        assertEquals(new BigDecimal("0.0300"), CatalogoDominioDomRock.obterTaxaBaseContratual(40, "VENDEDOR BALCAO").orElseThrow());
        assertEquals(new BigDecimal("0.0250"), CatalogoDominioDomRock.obterTaxaBaseContratual(40, "ASSISTENTE DE VENDAS").orElseThrow());

        // AMARELO (50)
        assertEquals(new BigDecimal("0.0250"), CatalogoDominioDomRock.obterTaxaBaseContratual(50, "VENDEDOR LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0100"), CatalogoDominioDomRock.obterTaxaBaseContratual(50, "GERENTE DE LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0075"), CatalogoDominioDomRock.obterTaxaBaseContratual(50, "GERENTE QUIOSQUE").orElseThrow());
        assertEquals(new BigDecimal("0.0200"), CatalogoDominioDomRock.obterTaxaBaseContratual(50, "VENDEDOR BALCAO").orElseThrow());
        assertEquals(new BigDecimal("0.0150"), CatalogoDominioDomRock.obterTaxaBaseContratual(50, "ASSISTENTE DE VENDAS").orElseThrow());

        // CINZA (60)
        assertEquals(new BigDecimal("0.0300"), CatalogoDominioDomRock.obterTaxaBaseContratual(60, "VENDEDOR LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0150"), CatalogoDominioDomRock.obterTaxaBaseContratual(60, "GERENTE DE LOJA").orElseThrow());
        assertEquals(new BigDecimal("0.0125"), CatalogoDominioDomRock.obterTaxaBaseContratual(60, "GERENTE QUIOSQUE").orElseThrow());
        assertEquals(new BigDecimal("0.0250"), CatalogoDominioDomRock.obterTaxaBaseContratual(60, "VENDEDOR BALCAO").orElseThrow());
        assertEquals(new BigDecimal("0.0200"), CatalogoDominioDomRock.obterTaxaBaseContratual(60, "ASSISTENTE DE VENDAS").orElseThrow());
    }

    @Test
    @DisplayName("6. Deve gerar descrição legível enriquecida da referência de comissão")
    void deveGerarDescricaoEnriquecida() {
        String descr = CatalogoDominioDomRock.gerarDescricaoReferencia(10, "PRETO", 100, "VENDEDOR LOJA");
        assertEquals("Taxa Contratual Padrão (Marca PRETO - VENDEDOR LOJA: 2.5%)", descr);

        String descrGenerica = CatalogoDominioDomRock.gerarDescricaoReferencia(null, null, null, null);
        assertEquals("Taxa Contratual Padrão", descrGenerica);
    }

    @Test
    @DisplayName("7. Deve construir CatalogoDominioResponseDTO completo com todas as dimensões")
    void deveConstruirCatalogoDominioResponseDTO() {
        CatalogoDominioResponseDTO dto = CatalogoDominioResponseDTO.construir();
        assertNotNull(dto);
        assertEquals(6, dto.marcas().size());
        assertEquals(4, dto.cargos().size());
        assertEquals(5, dto.cargosDetalhados().size());
        assertEquals(80, dto.lojas().size());
        assertEquals(6, dto.competenciasDocumentadas().size());
        assertEquals(30, dto.taxasContratuaisBase().size());
    }

    @Test
    @DisplayName("8. InterpretadorService deve resolver taxa contratual dinâmica a partir de Marca e Cargo")
    void deveResolverTaxaContratualDinamicaNoInterpretador() {
        InterpretadorService service = new InterpretadorService(null);

        // Texto com acréscimo para AZUL (30) e VENDEDOR LOJA (100) -> base = 0.0200, ajuste = 0.0100 -> total 0.0300
        OperacaoBaseDTO op = service.extrairOperacaoBase(
                "taxa base + 1.0%",
                null,
                30,
                "AZUL",
                100,
                "VENDEDOR LOJA"
        );

        assertEquals(TipoOperacaoBase.ACRESCIMO_PONTOS, op.tipoOperacao());
        assertEquals(new BigDecimal("0.0100"), op.valorAjuste());
        assertNotNull(op.referenciaBase());
        assertEquals(new BigDecimal("0.0200"), op.referenciaBase().taxaBaseConsultada());
        assertTrue(op.referenciaBase().descricao().contains("Marca AZUL"));
    }
}

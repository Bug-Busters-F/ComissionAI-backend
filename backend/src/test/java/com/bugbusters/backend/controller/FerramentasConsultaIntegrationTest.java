package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ai.service.url=http://localhost:8000")
class FerramentasConsultaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private com.bugbusters.backend.sales.SaleRepository saleRepository;

    private Brand marca;
    private Position cargo;
    private Store loja;
    private Registration matricula;
    private BaseComiss baseComiss;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        marca = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });

        cargo = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR LOJA");
            return positionRepository.save(p);
        });

        loja = storeRepository.findByCode(1).orElseGet(() -> {
            Store s = new Store();
            s.setCode(1);
            s.setDescription("LOJA-1 CENTRO");
            return storeRepository.save(s);
        });

        matricula = registrationRepository.findByRegistration("MATRIC-999").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-999");
            r.setStore(loja);
            r.setPosition(cargo);
            r.setAdmissDate(LocalDate.of(2024, 1, 1));
            return registrationRepository.save(r);
        });

        baseComiss = baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(10, 100).orElseGet(() -> {
            BaseComiss bc = new BaseComiss(marca, cargo, new BigDecimal("0.0250"));
            bc.setReferenceMonth(LocalDate.of(2025, 1, 1));
            return baseComissRepository.save(bc);
        });
    }


    @Test
    @DisplayName("GET /api/v1/canais deve listar os canais de venda padronizados")
    void deveListarCanais() throws Exception {
        mockMvc.perform(get("/api/v1/canais"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].codigo", is("ECOMMERCE")))
                .andExpect(jsonPath("$[1].codigo", is("LOJA_FISICA")))
                .andExpect(jsonPath("$[2].codigo", is("WHATSAPP")))
                .andExpect(jsonPath("$[3].codigo", is("PARCEIRO")));
    }

    @Test
    @DisplayName("GET /api/v1/marcas/todas e /api/v1/marcas/codigo/{codigo} devem retornar registros corretamente")
    void deveConsultarMarcas() throws Exception {
        mockMvc.perform(get("/api/v1/marcas/todas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.code == 10)].description", hasItem("PRETO")));

        mockMvc.perform(get("/api/v1/marcas/codigo/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(10)))
                .andExpect(jsonPath("$.description", is("PRETO")));

        mockMvc.perform(get("/api/v1/marcas/codigo/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/cargos/todos e /api/v1/cargos/codigo/{codigo} devem retornar registros corretamente")
    void deveConsultarCargos() throws Exception {
        mockMvc.perform(get("/api/v1/cargos/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.code == 100)].description", hasItem("VENDEDOR LOJA")));

        mockMvc.perform(get("/api/v1/cargos/codigo/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(100)))
                .andExpect(jsonPath("$.description", is("VENDEDOR LOJA")));

        mockMvc.perform(get("/api/v1/cargos/codigo/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/lojas/todas e /api/v1/lojas/codigo/{codigo} devem retornar registros corretamente")
    void deveConsultarLojas() throws Exception {
        mockMvc.perform(get("/api/v1/lojas/todas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.code == 1)].description", hasItem("LOJA-1 CENTRO")));

        mockMvc.perform(get("/api/v1/lojas/codigo/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1)))
                .andExpect(jsonPath("$.description", is("LOJA-1 CENTRO")));

        mockMvc.perform(get("/api/v1/lojas/codigo/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/matriculas com filtros e buscar/{matricula} devem retornar dados cadastrais")
    void deveConsultarMatriculas() throws Exception {
        mockMvc.perform(get("/api/v1/matriculas")
                        .param("matricula", "999")
                        .param("lojaCodigo", "1")
                        .param("cargoCodigo", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[?(@.registration == 'MATRIC-999')].store.code", hasItem(1)))
                .andExpect(jsonPath("$.content[?(@.registration == 'MATRIC-999')].position.code", hasItem(100)));

        mockMvc.perform(get("/api/v1/matriculas/buscar/MATRIC-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration", is("MATRIC-999")))
                .andExpect(jsonPath("$.position.description", is("VENDEDOR LOJA")));

        mockMvc.perform(get("/api/v1/matriculas/buscar/INEXISTENTE"))
                .andExpect(status().isNotFound());
    }


    @Test
    @DisplayName("GET /api/v1/bases-comissao e taxa-padrao devem retornar percentuais cadastrados")
    void deveConsultarBasesComissao() throws Exception {
        mockMvc.perform(get("/api/v1/bases-comissao")
                        .param("codMarca", "10")
                        .param("codCargo", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].codMarca", is(10)))
                .andExpect(jsonPath("$.content[0].codCargo", is(100)))
                .andExpect(jsonPath("$.content[0].percentual", is(0.0250)));

        mockMvc.perform(get("/api/v1/bases-comissao/taxa-padrao")
                        .param("codMarca", "10")
                        .param("codCargo", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codMarca", is(10)))
                .andExpect(jsonPath("$.codCargo", is(100)))
                .andExpect(jsonPath("$.percentual", is(0.0250)));
    }

    @Test
    @DisplayName("DELETE /api/v1/bases-comissao/{id} deve excluir taxa base sem dependências com 204 No Content")
    void deveExcluirBaseComissaoComSucesso() throws Exception {
        BaseComiss bc = new BaseComiss(marca, cargo, new BigDecimal("0.0350"));
        bc.setReferenceMonth(LocalDate.of(2028, 5, 1));
        bc = baseComissRepository.save(bc);
        UUID id = bc.getId();

        mockMvc.perform(delete("/api/v1/bases-comissao/{id}", id))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(baseComissRepository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("DELETE /api/v1/bases-comissao/{id} com ID inexistente deve retornar 404 Not Found")
    void deveRetornar404AoExcluirBaseComissaoInexistente() throws Exception {
        mockMvc.perform(delete("/api/v1/bases-comissao/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Taxa base não encontrada")));
    }

    @Test
    @DisplayName("DELETE /api/v1/bases-comissao/{id} com cálculo vinculado deve retornar 409 Conflict")
    void deveRetornar409AoExcluirBaseComissaoComCalculosVinculados() throws Exception {
        BaseComiss bc = new BaseComiss(marca, cargo, new BigDecimal("0.0400"));
        bc.setReferenceMonth(LocalDate.of(2027, 3, 1));
        bc = baseComissRepository.save(bc);
        UUID id = bc.getId();

        ResultadoCalculo calculo = new ResultadoCalculo(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MATRIC-999",
                10,
                1,
                100,
                null,
                LocalDate.of(2027, 3, 15),
                new BigDecimal("500.00"),
                new BigDecimal("0.0400"),
                new BigDecimal("20.00"),
                "INDIVIDUAL"
        );
        resultadoCalculoRepository.saveAndFlush(calculo);

        mockMvc.perform(delete("/api/v1/bases-comissao/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("cálculo(s)")));

        org.junit.jupiter.api.Assertions.assertTrue(baseComissRepository.existsById(id));
    }

    @Test
    @DisplayName("DELETE /api/v1/matriculas/todas com vendas vinculadas deve retornar 409 Conflict")
    void deveBloquearExclusaoTodasMatriculasQuandoHouverVendas() throws Exception {
        com.bugbusters.backend.sales.Sale venda = new com.bugbusters.backend.sales.Sale();
        venda.setRegistration(matricula);
        venda.setBrand(marca);
        venda.setStore(loja);
        venda.setValue(new BigDecimal("100.00"));
        venda.setSaleDate(LocalDate.of(2028, 1, 10));
        saleRepository.saveAndFlush(venda);

        mockMvc.perform(delete("/api/v1/matriculas/todas"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("venda(s) cadastradas")));
    }

    @Test
    @DisplayName("DELETE /api/v1/matriculas/todas sem vendas vinculadas deve excluir com 200 OK")
    void deveExcluirTodasMatriculasComSucesso() throws Exception {
        saleRepository.deleteAllInBatch();
        resultadoCalculoRepository.deleteAllInBatch();

        mockMvc.perform(delete("/api/v1/matriculas/todas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExcluido", greaterThanOrEqualTo(1)));

        org.junit.jupiter.api.Assertions.assertEquals(0, registrationRepository.count());
    }

    @Test
    @DisplayName("DELETE /api/v1/vendas/competencia/{mes}/{ano} com sucesso deve excluir e retornar total")
    void deveExcluirVendasPorCompetenciaComSucesso() throws Exception {
        resultadoCalculoRepository.deleteAllInBatch();

        com.bugbusters.backend.sales.Sale venda1 = new com.bugbusters.backend.sales.Sale();
        venda1.setRegistration(matricula);
        venda1.setBrand(marca);
        venda1.setStore(loja);
        venda1.setValue(new BigDecimal("150.00"));
        venda1.setSaleDate(LocalDate.of(2028, 9, 15));
        saleRepository.saveAndFlush(venda1);

        com.bugbusters.backend.sales.Sale venda2 = new com.bugbusters.backend.sales.Sale();
        venda2.setRegistration(matricula);
        venda2.setBrand(marca);
        venda2.setStore(loja);
        venda2.setValue(new BigDecimal("250.00"));
        venda2.setSaleDate(LocalDate.of(2028, 10, 5));
        saleRepository.saveAndFlush(venda2);

        mockMvc.perform(delete("/api/v1/vendas/competencia/9/2028"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExcluido", is(1)));

        org.junit.jupiter.api.Assertions.assertTrue(saleRepository.existsById(venda2.getId()));
        org.junit.jupiter.api.Assertions.assertFalse(saleRepository.existsById(venda1.getId()));
    }

    @Test
    @DisplayName("DELETE /api/v1/vendas/competencia/{mes}/{ano} com cálculo vinculado deve retornar 409 Conflict")
    void deveBloquearExclusaoVendasPorCompetenciaComCalculosVinculados() throws Exception {
        com.bugbusters.backend.sales.Sale venda = new com.bugbusters.backend.sales.Sale();
        venda.setRegistration(matricula);
        venda.setBrand(marca);
        venda.setStore(loja);
        venda.setValue(new BigDecimal("300.00"));
        venda.setSaleDate(LocalDate.of(2028, 4, 12));
        venda = saleRepository.saveAndFlush(venda);

        ResultadoCalculo calculo = new ResultadoCalculo(
                UUID.randomUUID(),
                venda.getId(),
                matricula.getRegistration(),
                marca.getCode(),
                loja.getCode(),
                cargo.getCode(),
                null,
                LocalDate.of(2028, 4, 12),
                new BigDecimal("300.00"),
                new BigDecimal("0.0500"),
                new BigDecimal("15.00"),
                "INDIVIDUAL"
        );
        resultadoCalculoRepository.saveAndFlush(calculo);

        mockMvc.perform(delete("/api/v1/vendas/competencia/4/2028"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("cálculos vinculados")));
    }

    @Test
    @DisplayName("DELETE /api/v1/vendas/todas deve excluir todas as vendas sem cálculos com 200 OK")
    void deveExcluirTodasVendasComSucesso() throws Exception {
        resultadoCalculoRepository.deleteAllInBatch();

        com.bugbusters.backend.sales.Sale venda = new com.bugbusters.backend.sales.Sale();
        venda.setRegistration(matricula);
        venda.setBrand(marca);
        venda.setStore(loja);
        venda.setValue(new BigDecimal("120.00"));
        venda.setSaleDate(LocalDate.of(2028, 7, 20));
        saleRepository.saveAndFlush(venda);

        mockMvc.perform(delete("/api/v1/vendas/todas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExcluido", greaterThanOrEqualTo(1)));

        org.junit.jupiter.api.Assertions.assertEquals(0, saleRepository.count());
    }

    @Test
    @DisplayName("DELETE /api/v1/bases-comissao/todas e /api/v1/comissoes/todas devem excluir com 200 OK")
    void deveExcluirTodasComissoesComSucesso() throws Exception {
        resultadoCalculoRepository.deleteAllInBatch();

        // Testa rota canônica /bases-comissao/todas
        mockMvc.perform(delete("/api/v1/bases-comissao/todas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExcluido", greaterThanOrEqualTo(1)));

        org.junit.jupiter.api.Assertions.assertEquals(0, baseComissRepository.count());

        // Insere nova taxa para testar rota alias /comissoes/todas
        BaseComiss bc = new BaseComiss(marca, cargo, new java.math.BigDecimal("0.0500"));
        bc.setReferenceMonth(LocalDate.of(2028, 12, 1));
        baseComissRepository.saveAndFlush(bc);

        mockMvc.perform(delete("/api/v1/comissoes/todas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExcluido", is(1)));

        org.junit.jupiter.api.Assertions.assertEquals(0, baseComissRepository.count());
    }
}

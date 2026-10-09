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
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

import static org.hamcrest.Matchers.*;
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
}

package com.bugbusters.backend.controller;

import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CampanhaRastreabilidadeIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired CampanhaRepository campanhas;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).build(); }

    private String payload(boolean atual) {
        return """
            {"titulo":"Conferência rastreável","textoOriginal":"Loja física abaixo de 1000: 3,5%",
             "estado":"DRAFT","dataInicio":"2026-10-01","dataFim":"2026-10-31",
             "regras":[{"blocoId":"one","canal":"LOJA_FISICA","taxa":0.035,"valorAjuste":0.035,
               "tipoOperacao":"DEFINIR_TAXA","valorMinimo":0,"minInclusivo":true,"valorMaximo":1000,"maxInclusivo":false,
               "bonusPercentual":0.01,"bonusValorFixo":0.015,"completa":true,
               "explicacao":"EXPLICAÇÃO DE OUTRA INTERPRETAÇÃO","pythonEquivalente":"EXEMPLO OBSOLETO",
               "rastreabilidade":{"origensPorCampo":{"canal":"TEXTO","taxa":"EDICAO_MANUAL"},
                 "camposManuais":["taxaPercentual"],"publicoTodasVendas":false,"artefatosAtualizados":ATUAL,
                 "referenciasConsultadas":[{"tipo":"CANAL","identificador":"LOJA_FISICA","descricao":"Loja física"}],
                 "pendencias":[{"id":"artefatos:aviso:one","campo":"canal","codigo":"AVISO_TESTE","mensagem":"Conferir cadastro","severidade":"AVISO"}],
                 "pendenciasCampanha":[{"codigo":"GERAL","mensagem":"Aviso geral","severidade":"AVISO"}]}}]}
            """.replace("ATUAL", Boolean.toString(atual));
    }
    private JsonNode criar(boolean atual) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/campanhas").contentType(MediaType.APPLICATION_JSON).content(payload(atual)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    @Test void preservaEvidenciasERegeneraArtefatosDosCamposSalvos() throws Exception {
        JsonNode created = criar(true);
        JsonNode saved = json.readTree(mvc.perform(get("/api/v1/campanhas/" + created.path("id").asLong()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode r = saved.path("regras").get(0);
        assertThat(r.path("explicacao").asText()).contains("3.50%", "1000.00").doesNotContain("OUTRA INTERPRETAÇÃO");
        assertThat(r.path("pythonEquivalente").asText()).contains("ROUND_HALF_UP", "Decimal").doesNotContain("OBSOLETO");
        assertThat(r.path("rastreabilidade").path("origensPorCampo").path("taxa").asText()).isEqualTo("EDICAO_MANUAL");
        assertThat(r.path("rastreabilidade").path("pendencias").get(0).path("id").asText()).isEqualTo("artefatos:aviso:one");
        assertThat(r.path("rastreabilidade").path("pendenciasCampanha").get(0).path("severidade").asText()).isEqualTo("AVISO");
        Files.createDirectories(Path.of("target/test-artifacts"));
        Files.writeString(Path.of("target/test-artifacts/rule.py"), r.path("pythonEquivalente").asText());
    }
    @Test void ocultaArtefatosDesatualizadosMesmoSeClienteEnviaCodigoAnterior() throws Exception {
        JsonNode r = criar(false).path("regras").get(0);
        assertThat(r.path("pythonEquivalente").isNull()).isTrue();
        assertThat(r.path("explicacao").isNull()).isTrue();
        assertThat(r.path("completa").asBoolean()).isFalse();
    }
    @Test void progressoPersisteSemAlterarRegrasDatasOuAceite() throws Exception {
        JsonNode c = criar(true); long id = c.path("id").asLong();
        c = json.readTree(mvc.perform(get("/api/v1/campanhas/"+id)).andReturn().getResponse().getContentAsString());
        String before = c.path("regras").toString();
        String updated = c.path("atualizadoEm").asText();
        mvc.perform(patch("/api/v1/campanhas/"+id+"/progresso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ultimaEtapa\":3,\"etapaMaisAvancada\":3,\"versaoProposta\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ultimaEtapa").value(3));
        mvc.perform(patch("/api/v1/campanhas/"+id+"/progresso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ultimaEtapa\":1,\"etapaMaisAvancada\":3,\"versaoProposta\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etapaMaisAvancada").value(3));
        JsonNode reloaded = json.readTree(mvc.perform(get("/api/v1/campanhas/"+id)).andReturn().getResponse().getContentAsString());
        assertThat(reloaded.path("ultimaEtapa").asInt()).isEqualTo(1);
        assertThat(reloaded.path("etapaMaisAvancada").asInt()).isEqualTo(3);
        assertThat(reloaded.path("regras").toString()).isEqualTo(before);
        assertThat(reloaded.path("atualizadoEm").asText()).isEqualTo(updated);
        mvc.perform(patch("/api/v1/campanhas/"+id+"/progresso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ultimaEtapa\":4,\"etapaMaisAvancada\":4,\"versaoProposta\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/campanhas/"+id+"/progresso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ultimaEtapa\":2,\"etapaMaisAvancada\":3,\"versaoProposta\":99}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/campanhas/"+id+"/progresso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ultimaEtapa\":5,\"etapaMaisAvancada\":5,\"versaoProposta\":1}"))
                .andExpect(status().isBadRequest());
    }
    @Test void impedeAvancoDeRegrasIncompletasEProgressoEmCampanhaSomenteLeitura() throws Exception {
        long id = criar(false).path("id").asLong();
        mvc.perform(patch("/api/v1/campanhas/"+id+"/progresso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ultimaEtapa\":3,\"etapaMaisAvancada\":3,\"versaoProposta\":1}"))
                .andExpect(status().isBadRequest());
        Campanha c = campanhas.findById(id).orElseThrow(); c.setEstado(EstadoCampanha.INATIVA); campanhas.save(c);
        mvc.perform(patch("/api/v1/campanhas/"+id+"/progresso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ultimaEtapa\":1,\"etapaMaisAvancada\":1,\"versaoProposta\":1}"))
                .andExpect(status().isBadRequest());
    }
}

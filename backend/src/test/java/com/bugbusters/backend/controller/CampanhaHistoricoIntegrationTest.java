package com.bugbusters.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampanhaHistoricoIntegrationTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).build(); }

    private ObjectNode regra(String bloco, int loja, String taxa) throws Exception {
        ObjectNode r = (ObjectNode) json.readTree("""
            {"blocoId":"placeholder","nome":"Regra histórica","trechoOrigem":"Taxa definida pelo usuário",
             "codMarca":10,"descrMarca":"PRETO","codCargo":100,"descriCargo":"VENDEDOR LOJA",
             "codLoja":16,"canal":"LOJA_FISICA","matricula":"001234",
             "taxa":0.035,"tipoOperacao":"DEFINIR_TAXA","valorAjuste":0.035,
             "valorMinimo":0,"minInclusivo":true,"valorMaximo":1000,"maxInclusivo":false,
             "bonusPercentual":0.01,"bonusValorFixo":5,"completa":true,
             "rastreabilidade":{"origensPorCampo":{"codLoja":"TEXTO","taxa":"EDICAO_MANUAL"},
               "referenciasConsultadas":[{"tipo":"LOJA","identificador":"16","descricao":"Loja 16"}],
               "camposManuais":["taxaPercentual"],"publicoTodasVendas":false,"artefatosAtualizados":true,
               "pendencias":[{"id":"aviso-1","codigo":"CONFERIR","campo":"taxa","mensagem":"Conferir percentual","severidade":"AVISO"}],
               "pendenciasCampanha":[{"codigo":"AVISO_GERAL","mensagem":"Aviso geral preservado","severidade":"AVISO"}]}}
            """);
        r.put("blocoId", bloco); r.put("codLoja", loja);
        r.put("taxa", new java.math.BigDecimal(taxa)); r.put("valorAjuste", new java.math.BigDecimal(taxa));
        return r;
    }
    private ObjectNode payload(String titulo, ObjectNode... regras) {
        ObjectNode c = json.createObjectNode();
        c.put("titulo", titulo); c.put("textoOriginal", "Campanha de teste " + titulo);
        c.put("estado", "DRAFT"); c.put("dataInicio", "2038-01-01"); c.put("dataFim", "2038-01-31");
        var collection = c.putArray("regras"); for (var r : regras) collection.add(r);
        return c;
    }
    private long criar(ObjectNode p) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/campanhas").contentType(MediaType.APPLICATION_JSON).content(p.toString()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }
    private void editar(long id, ObjectNode p) throws Exception {
        mvc.perform(put("/api/v1/campanhas/"+id).contentType(MediaType.APPLICATION_JSON).content(p.toString())).andExpect(status().isOk());
    }
    private JsonNode consultar(String path) throws Exception {
        return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode publicar(long id, Integer esperada) throws Exception {
        // No sales in this isolated future competence: test the publication contract, not financial totals.
        JsonNode sim = json.readTree(mvc.perform(post("/api/v1/simulacoes").contentType(MediaType.APPLICATION_JSON)
            .content("{\"campanhaId\":"+id+",\"competencia\":\"2038-01\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        ObjectNode aceite = json.createObjectNode().put("aceitoPor", "Gestor histórico").put("campanhaId",id);
        if (esperada != null) aceite.put("versaoEsperada",esperada);
        mvc.perform(post("/api/v1/simulacoes/"+sim.path("id").asText()+"/aceite").contentType(MediaType.APPLICATION_JSON).content(aceite.toString()))
            .andExpect(status().isOk());
        ObjectNode ativacao = json.createObjectNode(); if (esperada != null) ativacao.put("versaoEsperada",esperada);
        mvc.perform(post("/api/v1/campanhas/"+id+"/ativar").contentType(MediaType.APPLICATION_JSON).content(ativacao.toString()))
            .andExpect(status().isOk());
        return sim;
    }

    @Test void ocultaPropostasECanceladasDoHistorico() throws Exception {
        long id = criar(payload("Rascunho",regra("one",16,"0.035")));
        assertThat(consultar("/api/v1/campanhas/"+id+"/versoes")).isEmpty();
        mvc.perform(get("/api/v1/campanhas/"+id+"/versoes/1")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/campanhas/"+id+"/proposta")).andExpect(status().isNoContent());
        assertThat(consultar("/api/v1/campanhas/"+id+"/versoes")).isEmpty();
    }

    @Test void preservaCamposOrigensExplicacaoECodigoDepoisDeEditarECancelar() throws Exception {
        long id = criar(payload("Inicial",regra("one",16,"0.035")));
        publicar(id,null);
        String path = "/api/v1/campanhas/"+id+"/versoes/1";
        JsonNode antes = consultar(path); JsonNode r = antes.path("regras").get(0);
        assertThat(r.path("matricula").asText()).isEqualTo("001234");
        assertThat(r.path("explicacao").asText()).contains("3.50%");
        assertThat(r.path("pythonEquivalente").asText()).contains("Decimal", "0.035");
        assertThat(r.path("rastreabilidade").path("origensPorCampo").path("taxa").asText()).isEqualTo("EDICAO_MANUAL");
        assertThat(r.path("rastreabilidade").path("referenciasConsultadas").get(0).path("identificador").asText()).isEqualTo("16");
        assertThat(r.path("rastreabilidade").path("pendenciasCampanha").get(0).path("codigo").asText()).isEqualTo("AVISO_GERAL");
        editar(id,payload("Proposta",regra("one",13,"0.06")));
        assertThat(consultar(path)).isEqualTo(antes);
        mvc.perform(delete("/api/v1/campanhas/"+id+"/proposta")).andExpect(status().isNoContent());
        assertThat(consultar(path)).isEqualTo(antes);
        assertThat(consultar("/api/v1/campanhas/"+id).path("versaoVigente").asInt()).isEqualTo(1);
    }

    @Test void listaPublicacoesComAutoriaSimulacaoEVersaoAnteriorMesmoComCancelamentoIntermediario() throws Exception {
        long id = criar(payload("Inicial",regra("one",16,"0.035")));
        JsonNode sim1 = publicar(id,null);
        editar(id,payload("Cancelada",regra("one",16,"0.04")));
        mvc.perform(delete("/api/v1/campanhas/"+id+"/proposta")).andExpect(status().isNoContent());
        editar(id,payload("Publicada",regra("one",16,"0.05")));
        JsonNode sim3 = publicar(id,1);
        JsonNode historico = consultar("/api/v1/campanhas/"+id+"/versoes");
        assertThat(historico.size()).isEqualTo(2);
        assertThat(historico.get(0).path("numero").asInt()).isEqualTo(3);
        assertThat(historico.get(0).path("versaoAnterior").asInt()).isEqualTo(1);
        assertThat(historico.get(0).path("responsavel").asText()).isEqualTo("Gestor histórico");
        assertThat(historico.get(0).path("publicadoEm").asText()).isNotBlank();
        assertThat(historico.get(0).path("simulacaoAceitaId").asText()).isEqualTo(sim3.path("id").asText());
        assertThat(historico.get(1).path("protocoloSimulacao").asText()).isEqualTo(sim1.path("protocolo").asText());
        mvc.perform(get("/api/v1/campanhas/"+id+"/versoes/2")).andExpect(status().isNotFound());
    }

    @Test void comparaCampanhaEFiltrosFaixasTaxasInclusaoERemocaoSemMisturarRegras() throws Exception {
        long id = criar(payload("Inicial",regra("one",16,"0.035"),regra("removed",13,"0.04"),regra("same",9,"0.045")));
        publicar(id,null);
        ObjectNode changed = regra("one",17,"0.05");
        changed.put("valorMinimo",100); changed.put("minInclusivo",false); changed.put("valorMaximo",2000); changed.put("maxInclusivo",true);
        changed.with("rastreabilidade").with("origensPorCampo").put("codLoja","EDICAO_MANUAL");
        ObjectNode updated = payload("Revisada",changed,regra("added",18,"0.06"),regra("same",9,"0.045"));
        editar(id,updated); publicar(id,1);
        JsonNode diff = consultar("/api/v1/campanhas/"+id+"/versoes/comparacao?versaoBase=1&versaoComparada=2");
        assertThat(diff.path("alteracoesCampanha").findValuesAsText("campo")).contains("titulo","textoOriginal");
        var regras = new java.util.HashMap<String,JsonNode>();
        diff.path("alteracoesRegras").forEach(r -> regras.put(r.path("identificadorLogico").asText(),r));
        assertThat(regras.get("added").path("tipo").asText()).isEqualTo("ADICIONADA");
        assertThat(regras.get("removed").path("tipo").asText()).isEqualTo("REMOVIDA");
        assertThat(regras.get("same").path("tipo").asText()).isEqualTo("SEM_ALTERACAO");
        assertThat(regras.get("one").path("tipo").asText()).isEqualTo("ALTERADA");
        assertThat(regras.get("one").path("alteracoes").findValuesAsText("campo"))
            .contains("codLoja","valorMinimo","minInclusivo","valorMaximo","maxInclusivo","taxa","rastreabilidade");
    }

    @Test void rejeitaIdentificadoresDuplicadosSemDescartarOuSobrescreverRegras() throws Exception {
        long id = criar(payload("Inicial",regra("one",16,"0.035"))); publicar(id,null);
        JsonNode antes = consultar("/api/v1/campanhas/"+id+"/versoes/1");
        ObjectNode invalid = payload("Duplicada",regra("one",16,"0.035"),regra(" one ",13,"0.04"));
        mvc.perform(put("/api/v1/campanhas/"+id).contentType(MediaType.APPLICATION_JSON).content(invalid.toString()))
            .andExpect(status().isBadRequest());
        assertThat(consultar("/api/v1/campanhas/"+id+"/versoes/1")).isEqualTo(antes);
        assertThat(consultar("/api/v1/campanhas/"+id).path("versaoProposta").isNull()).isTrue();
        mvc.perform(post("/api/v1/campanhas").contentType(MediaType.APPLICATION_JSON).content(invalid.toString()))
            .andExpect(status().isBadRequest());
    }

    @Test void comparaVigenciaDasVersoesPublicadas() throws Exception {
        long id = criar(payload("Inicial",regra("one",16,"0.035"))); publicar(id,null);
        ObjectNode updated = payload("Inicial",regra("one",16,"0.035"));
        updated.put("dataInicio","2038-01-02"); updated.put("dataFim","2038-01-30");
        editar(id,updated); publicar(id,1);
        JsonNode diff = consultar("/api/v1/campanhas/"+id+"/versoes/comparacao?versaoBase=1&versaoComparada=2");
        assertThat(diff.path("alteracoesCampanha").findValuesAsText("campo")).containsExactly("dataInicio","dataFim");
        assertThat(consultar("/api/v1/campanhas/"+id+"/versoes/1").path("dataInicio").asText()).isEqualTo("2038-01-01");
    }

    @Test void comparacaoRecusaVersaoNaoPublicadaEVersaoDeOutraCampanha() throws Exception {
        long id = criar(payload("Publicada",regra("one",16,"0.035"))); publicar(id,null);
        editar(id,payload("Proposta",regra("one",16,"0.05")));
        mvc.perform(get("/api/v1/campanhas/"+id+"/versoes/comparacao?versaoBase=1&versaoComparada=2")).andExpect(status().isNotFound());
        long outro = criar(payload("Outra",regra("other",18,"0.05")));
        mvc.perform(get("/api/v1/campanhas/"+outro+"/versoes/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/campanhas/"+id+"/versoes/comparacao?versaoBase=1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/campanhas/"+id+"/versoes/comparacao?versaoBase=abc&versaoComparada=1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/campanhas/"+id+"/versoes/abc")).andExpect(status().isBadRequest());
    }
}

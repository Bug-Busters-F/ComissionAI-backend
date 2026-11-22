package com.bugbusters.backend.controller;

import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.service.pendencia.AceitePendenciaProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AceiteBasePublicadaIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired CampanhaRepository campanhas;
    @Autowired CampanhaVersaoRepository versoes;
    @Autowired CampanhaProgramaPythonRepository programas;
    @Autowired EnvioArquivoRepository envios;
    @Autowired InconsistenciaImportacaoRepository inconsistencias;
    @Autowired CampanhaAnexoBaseRepository anexos;
    @Autowired CampanhaPendenciaAceiteRepository aceites;
    @Autowired FechamentoCompetenciaRepository fechamentos;
    @Autowired AceitePendenciaProvider provider;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    Campanha campanha;
    CampanhaVersao vigente;
    CampanhaProgramaPython programa;
    EnvioArquivo envio;
    InconsistenciaImportacao inc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        role("ADMINISTRADOR");
        campanha = campanhas.save(new Campanha("Aceite publicado", "Regras preservadas", LocalDate.of(2033,11,1), LocalDate.of(2033,11,30)));
        vigente = new CampanhaVersao(); vigente.setCampanha(campanha); vigente.setNumero(1); vigente.setStatus(StatusVersaoCampanha.PUBLICADA);
        vigente.setTitulo(campanha.getTitulo()); vigente.setTextoOriginal(campanha.getTextoOriginal()); vigente.setDataInicio(campanha.getDataInicio()); vigente.setDataFim(campanha.getDataFim());
        vigente = versoes.saveAndFlush(vigente); campanha.setVersaoVigente(vigente); campanha.setEstado(EstadoCampanha.ATIVA); campanhas.saveAndFlush(campanha);
        programa = new CampanhaProgramaPython(vigente,1,"def calcular_comissao(venda):\n    return 1");
        programa.setStatusAprovacao(StatusAprovacaoPrograma.APROVADO); programa.setAprovadoPor("revisor-original");
        programa = programas.saveAndFlush(programa); vigente.setProgramaPythonAprovado(programa); versoes.saveAndFlush(vigente);
        envio = new EnvioArquivo(); envio.setNomeArquivo("Vendas.xlsx"); envio.setTipoBase("SALES"); envio.setCompetencia("2033-11"); envio.setStatus("CONCLUIDO"); envio.setIdLote(UUID.randomUUID().toString());
        envio = envios.saveAndFlush(envio); envio.setReportId(envio.getId()); envios.saveAndFlush(envio);
        anexos.saveAndFlush(new CampanhaAnexoBase(campanha,envio,"SALES","2033-11","gestor"));
        inc = inconsistencias.saveAndFlush(new InconsistenciaImportacao(envio,681,"matricula","Matrícula não cadastrada: MATRIC-207","ADVERTENCIA"));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    void role(String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("gestor-real@empresa.local",null,List.of(new SimpleGrantedAuthority("ROLE_"+role)))); }
    String payload() throws Exception {
        return json.writeValueAsString(Map.of("pendenciaIdentificador","inconsistencia:"+inc.getId(),"codigo","INCONSISTENCIA_IMPORTACAO","severidade","AVISO","motivo","Conferido com os documentos oficiais","inconsistenciaImportacaoId",inc.getId(),"aceitoPor","identidade-falsa"));
    }
    @Test void registraConsultaERevogaAceiteDaBaseMantendoProgramaPublicado() throws Exception {
        String checksum = programa.getChecksumSha256();
        String result = mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite",campanha.getId()).contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACEITA"))
                .andExpect(jsonPath("$.aceitoPor").value("gestor-real@empresa.local"))
                .andExpect(jsonPath("$.campanhaVersaoId").value(vigente.getId()))
                .andReturn().getResponse().getContentAsString();
        Long aceiteId = json.readTree(result).path("id").asLong();
        assertThat(provider.consultarAceite(campanha.getId(),envio.getId(),inc).aceita()).isTrue();
        mvc.perform(get("/api/v1/campanhas/{id}/cobertura-fechamento/pendencias",campanha.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].aceita",hasItem(true)));
        mvc.perform(post("/api/v1/campanhas/{id}/pendencias/{aceiteId}/revogar",campanha.getId(),aceiteId).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Revisão dos documentos originais\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOGADA"));
        assertThat(provider.consultarAceite(campanha.getId(),envio.getId(),inc).aceita()).isFalse();
        assertThat(versoes.findById(vigente.getId()).orElseThrow().getStatus()).isEqualTo(StatusVersaoCampanha.PUBLICADA);
        assertThat(programas.findById(programa.getId()).orElseThrow().getChecksumSha256()).isEqualTo(checksum);
        assertThat(programa.getStatusAprovacao()).isEqualTo(StatusAprovacaoPrograma.APROVADO);
        assertThat(campanha.getVersaoProposta()).isNull();
    }
    @Test void repeteAceiteDaMesmaInconsistenciaSemCriarDuplicata() throws Exception {
        for(int i=0;i<2;i++) mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite",campanha.getId()).contentType(MediaType.APPLICATION_JSON).content(payload())).andExpect(status().isCreated());
        assertThat(aceites.findAllByCampanhaVersaoIdAndStatus(vigente.getId(),StatusAceitePendencia.ACEITA)).hasSize(1);
    }
    @Test void bloqueiaAceiteEmCompetenciaFechada() throws Exception {
        var fechamento = new FechamentoCompetencia("2033-11",UUID.randomUUID(),"gestor","Fechamento"); fechamento.setStatus(StatusFechamento.CONCLUIDO); fechamentos.saveAndFlush(fechamento);
        mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite",campanha.getId()).contentType(MediaType.APPLICATION_JSON).content(payload())).andExpect(status().isBadRequest());
        assertThat(aceites.findAllByCampanhaVersaoIdAndStatus(vigente.getId(),StatusAceitePendencia.ACEITA)).isEmpty();
    }
    @Test void naoEmprestaAceiteDePropostaParaVersaoVigente() {
        var proposta = new CampanhaVersao(); proposta.setCampanha(campanha); proposta.setNumero(2); proposta.setStatus(StatusVersaoCampanha.PROPOSTA);
        proposta.setTitulo("Proposta"); proposta.setTextoOriginal(campanha.getTextoOriginal()); proposta.setDataInicio(vigente.getDataInicio()); proposta.setDataFim(vigente.getDataFim());
        proposta = versoes.saveAndFlush(proposta); campanha.setVersaoProposta(proposta); campanhas.saveAndFlush(campanha);
        var aceite = new CampanhaPendenciaAceite(campanha,proposta,null,"inconsistencia:"+inc.getId(),"INCONSISTENCIA_IMPORTACAO","matricula",inc.getMotivo(),com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia.AVISO,"gestor","Conferido na proposta");
        aceite.setInconsistenciaImportacaoId(inc.getId()); aceite.setEnvioArquivoId(envio.getId()); aceites.saveAndFlush(aceite);
        assertThat(provider.consultarAceite(campanha.getId(),envio.getId(),inc).aceita()).isFalse();
    }
    @Test void naoPermiteAceiteDaBasePorAnalista() throws Exception {
        role("ANALISTA_NEGOCIOS");
        mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite",campanha.getId()).contentType(MediaType.APPLICATION_JSON).content(payload())).andExpect(status().isForbidden());
    }
}

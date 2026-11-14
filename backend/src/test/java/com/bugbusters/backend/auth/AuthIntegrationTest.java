package com.bugbusters.backend.auth;

import com.bugbusters.backend.auth.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.OffsetDateTime;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired UsuarioRepository usuarios;
    @Autowired SessaoUsuarioRepository sessoes;
    @Autowired AuthService service;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

    @BeforeEach void setup() {
        // Exercita os filtros reais: os testes antigos de controllers não os instalam.
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }
    Usuario user(String email, PapelUsuario papel) {
        Usuario u = new Usuario(); u.setNome("Conta de teste"); u.setEmail(email);
        u.setPapel(papel); u.setSenhaHash(encoder.encode("Teste@123"));
        return usuarios.saveAndFlush(u);
    }
    String token(Usuario u) { return service.login(new LoginRequest(u.getEmail(), "Teste@123")).token(); }

    @Test void loginErrosESessaoReal() throws Exception {
        Usuario u = user("login@teste.local", PapelUsuario.ANALISTA_NEGOCIOS);
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"login@teste.local\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("status").value(401));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"login@teste.local\",\"senha\":\"Teste@123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("usuario.papel").value("ANALISTA_NEGOCIOS"))
                .andExpect(jsonPath("token").isNotEmpty());
        String t = token(u);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value(u.getEmail()));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + t))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("status").value(401));
        u.setAtivo(false); usuarios.saveAndFlush(u);
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"login@teste.local\",\"senha\":\"Teste@123\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void sessaoExpiradaEUsuarioDesativadoSaoRecusados() throws Exception {
        Usuario u = user("expira@teste.local", PapelUsuario.ANALISTA_NEGOCIOS);
        String t = token(u);
        var s = sessoes.findByUsuarioIdAndEncerradaEmIsNull(u.getId()).getFirst();
        s.setExpiraEm(OffsetDateTime.now().minusMinutes(1)); sessoes.saveAndFlush(s);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
        String outro = token(u); u.setAtivo(false); usuarios.saveAndFlush(u);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + outro)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void trocarSenhaRevogaTodasAsSessoes() throws Exception {
        Usuario u = user("senha@teste.local", PapelUsuario.ANALISTA_CODIGO);
        String t = token(u), outro = token(u);
        mvc.perform(patch("/api/v1/auth/me/senha").header("Authorization", "Bearer " + t)
                .contentType("application/json").content("{\"senhaAtual\":\"Teste@123\",\"novaSenha\":\"Nova@12345\"}"))
                .andExpect(status().isNoContent());
        for (String antigo : new String[]{t, outro}) mvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + antigo)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"senha@teste.local\",\"senha\":\"Nova@12345\"}"))
                .andExpect(status().isOk());
    }
    @Test void adminNaoPodeDesativarOuRebaixarPropriaConta() throws Exception {
        Usuario admin = user("admin-teste@teste.local", PapelUsuario.ADMINISTRADOR);
        String t = token(admin);
        mvc.perform(patch("/api/v1/usuarios/" + admin.getId() + "/ativo?ativo=false")
                .header("Authorization", "Bearer " + t)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/usuarios/" + admin.getId()).header("Authorization", "Bearer " + t))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/usuarios/" + admin.getId() + "/perfil").header("Authorization", "Bearer " + t)
                .contentType("application/json").content("{\"papel\":\"AUDITOR\"}"))
                .andExpect(status().isConflict());
    }
    @Test void mudancaDePerfilValeNoTokenExistenteEExclusaoPreservaIdentidade() throws Exception {
        Usuario admin = user("gestao@teste.local", PapelUsuario.ADMINISTRADOR);
        Usuario alvo = user("alvo@teste.local", PapelUsuario.ANALISTA_NEGOCIOS);
        String adm = token(admin), t = token(alvo);
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/usuarios/" + alvo.getId() + "/perfil").header("Authorization", "Bearer " + adm)
                .contentType("application/json").content("{\"papel\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/usuarios/" + alvo.getId()).header("Authorization", "Bearer " + adm))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/usuarios/" + alvo.getId()).header("Authorization", "Bearer " + adm))
                .andExpect(status().isOk()).andExpect(jsonPath("ativo").value(false))
                .andExpect(jsonPath("email").value("alvo@teste.local"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
    }
    @Test void auditorPodeConsultarBasesMasNaoAlterar() throws Exception {
        String t = token(user("auditoria@teste.local", PapelUsuario.AUDITOR));
        mvc.perform(get("/api/v1/imports").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/imports/999999").header("Authorization", "Bearer " + t))
                .andExpect(status().isForbidden()).andExpect(jsonPath("status").value(403));
        mvc.perform(post("/api/v1/campanhas").header("Authorization", "Bearer " + t)
                .contentType("application/json").content("{}" )).andExpect(status().isForbidden());
    }
    @Test void auditorConsultaAlertasMasNaoPodeReprocessar() throws Exception {
        String auditor = token(user("auditor-alertas@teste.local", PapelUsuario.AUDITOR));
        mvc.perform(get("/api/v1/alertas-estatisticos").header("Authorization", "Bearer " + auditor))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/alertas-estatisticos/reprocessar").header("Authorization", "Bearer " + auditor)
                .contentType("application/json").content("{\"lotePreparacaoId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isForbidden());
        String gerente = token(user("gerente-alertas@teste.local", PapelUsuario.GERENTE_VENDAS));
        mvc.perform(post("/api/v1/alertas-estatisticos/reprocessar").header("Authorization", "Bearer " + gerente)
                .contentType("application/json").content("{\"lotePreparacaoId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isNotFound());
    }
    @Test void codigoSalvaENegociosRegistraProgressoSemPublicar() throws Exception {
        for (PapelUsuario papel : new PapelUsuario[]{PapelUsuario.ANALISTA_CODIGO, PapelUsuario.ANALISTA_NEGOCIOS}) {
            String t = token(user(papel.name() + "@teste.local", papel));
            // A requisição chega à validação do domínio, em vez de ser barrada com 403.
            mvc.perform(put("/api/v1/campanhas/999999").header("Authorization", "Bearer " + t)
                    .contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
            mvc.perform(patch("/api/v1/campanhas/999999/progresso").header("Authorization", "Bearer " + t)
                    .contentType("application/json").content("{\"ultimaEtapa\":2,\"etapaMaisAvancada\":2,\"versaoProposta\":1}"))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/v1/campanhas/999999/ativar").header("Authorization", "Bearer " + t))
                    .andExpect(status().isForbidden());
        }
    }
    @Test void negocioAceitaSimulacaoMasCodigoNao() throws Exception {
        for (PapelUsuario papel : new PapelUsuario[]{PapelUsuario.ANALISTA_NEGOCIOS, PapelUsuario.GERENTE_VENDAS}) {
            String t = token(user(papel.name() + "-aceite@teste.local", papel));
            mvc.perform(post("/api/v1/simulacoes/00000000-0000-0000-0000-000000000001/aceite")
                    .header("Authorization", "Bearer " + t).contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
        String codigo = token(user("codigo-aceite@teste.local", PapelUsuario.ANALISTA_CODIGO));
        mvc.perform(post("/api/v1/simulacoes/00000000-0000-0000-0000-000000000001/aceite")
                .header("Authorization", "Bearer " + codigo).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test void chaveInternaSoAutorizaConsultasIa() throws Exception {
        mvc.perform(get("/api/v1/consultas-ia/marcas").header("X-Internal-Api-Key", "test-internal-ai-key"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/consultas-ia/regras-concorrentes").header("X-Internal-Api-Key", "test-internal-ai-key")
                .contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/consultas-ia/marcas").header("X-Internal-Api-Key", "incorreta"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/consultas-ia/marcas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/usuarios").header("X-Internal-Api-Key", "test-internal-ai-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/consultas-ia/marcas").header("X-Internal-Api-Key", "test-internal-ai-key"))
                .andExpect(status().isForbidden());
    }
}

package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.*;
import com.bugbusters.backend.brand.*;
import com.bugbusters.backend.dto.execucao.*;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.*;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.*;
import com.bugbusters.backend.registration.*;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.*;
import com.bugbusters.backend.service.*;
import com.bugbusters.backend.store.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="ai.service.url=http://localhost:8000")
@ActiveProfiles("test")
class ExecucaoCalculoIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String pg=System.getenv("EXECUCAO_TEST_DB_URL");
        r.add("spring.datasource.url",() -> pg!=null ? pg : "jdbc:h2:mem:execucao_calculo_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        if (pg!=null) {
            r.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
            r.add("spring.datasource.username",() -> System.getenv().getOrDefault("EXECUCAO_TEST_DB_USER","postgres"));
            r.add("spring.datasource.password",() -> System.getenv().getOrDefault("EXECUCAO_TEST_DB_PASSWORD","postgres"));
            r.add("spring.flyway.enabled",() -> true); r.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
            r.add("spring.batch.jdbc.initialize-schema",() -> "never");
        }
    }
    @MockitoBean(name="execucaoCalculoExecutor") TaskExecutor executor;
    @MockitoSpyBean CalculoService calculo;
    @Autowired ExecucaoCalculoRepository execucoes;
    @MockitoSpyBean ExecucaoCalculoProgressoService acompanhamento;
    @Autowired ExecucaoCalculoService service;
    @Autowired ExecucaoCalculoWorker worker;
    @Autowired ReferenciasExecucaoCalculoService referencias;
    @Autowired BrandRepository marcas;
    @Autowired PositionRepository cargos;
    @Autowired StoreRepository lojas;
    @Autowired RegistrationRepository pessoas;
    @Autowired BaseComissRepository bases;
    @Autowired SaleRepository vendas;
    @Autowired CampanhaRepository campanhas;
    @Autowired CampanhaVersaoRepository versoes;
    @Autowired RegraRepository regras;
    @Autowired FechamentoCompetenciaRepository fechamentos;
    @Autowired JdbcTemplate jdbc;
    private final BlockingQueue<Runnable> fila=new LinkedBlockingQueue<>();
    private static final AtomicInteger SEQUENCE=new AtomicInteger();
    @BeforeEach void queue() { doAnswer(i -> { fila.add(i.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class)); }

    private record Fixture(String competencia,Brand marca,Position cargo,Store loja,Registration pessoa,BaseComiss base,Campanha campanha,Regra regra,Sale primeira,Sale segunda) {}
    private Fixture fixture() {
        int seq=SEQUENCE.incrementAndGet(); YearMonth month=YearMonth.of(2050+seq/12,1+seq%12);
        Brand b=new Brand(); b.setCode(10000+seq); b.setDescription("Marca "+seq); b=marcas.saveAndFlush(b);
        Position p=new Position(); p.setCode(10000+seq); p.setDescription("Cargo "+seq); p=cargos.saveAndFlush(p);
        Store s=new Store(); s.setCode(10000+seq); s.setDescription("Loja "+seq); s=lojas.saveAndFlush(s);
        Registration person=new Registration(); person.setRegistration("EXEC-"+seq); person.setPosition(p); person.setStore(s); person.setAdmissDate(LocalDate.of(2040,1,1)); person=pessoas.saveAndFlush(person);
        BaseComiss base=new BaseComiss(b,p,new BigDecimal("0.025")); base.setReferenceMonth(month.atDay(1)); base=bases.saveAndFlush(base);
        Campanha c=new Campanha("Campanha "+seq,"Comissão 5%",month.atDay(1),month.atEndOfMonth()); c.setEstado(EstadoCampanha.ATIVA); c=campanhas.saveAndFlush(c);
        CampanhaVersao v=new CampanhaVersao(); v.setCampanha(c); v.setNumero(1); v.setStatus(StatusVersaoCampanha.PUBLICADA); v.setTitulo(c.getTitulo()); v.setTextoOriginal(c.getTextoOriginal()); v.setDataInicio(c.getDataInicio()); v.setDataFim(c.getDataFim()); v=versoes.saveAndFlush(v);
        c.setVersaoVigente(v); c=campanhas.saveAndFlush(c);
        Regra rule=new Regra(); rule.setNome("Regra 5%"); rule.setIdentificadorLogico("regra-"+seq); rule.setBlocoId("regra-"+seq); rule.setCampanha(c); rule.setCampanhaVersao(v); rule.setTaxa(new BigDecimal("0.05")); rule.setDataInicio(month.atDay(1)); rule.setDataFim(month.atEndOfMonth()); rule.setCodMarca(b.getCode()); rule.setCodCargo(p.getCode()); rule.setCodLoja(s.getCode()); rule.setCanal("LOJA_FISICA"); rule.setValorMinimo(BigDecimal.ZERO); rule.setMinInclusivo(true); rule.setStatus(StatusRegra.ATIVA); rule=regras.saveAndFlush(rule);
        Sale a=new Sale(); a.setBrand(b); a.setStore(s); a.setRegistration(person); a.setSaleDate(month.atDay(10)); a.setValue(new BigDecimal("100")); a.setSaleChannel("LOJA_FISICA"); a=vendas.saveAndFlush(a);
        Sale z=new Sale(); z.setBrand(b); z.setStore(s); z.setRegistration(person); z.setSaleDate(month.atDay(11)); z.setValue(new BigDecimal("200")); z.setSaleChannel("LOJA_FISICA"); z=vendas.saveAndFlush(z);
        return new Fixture(month.toString(),b,p,s,person,base,c,rule,a,z);
    }
    private ExecucaoCalculoResponse solicitar(Fixture f,String chave) { return service.solicitar(new SolicitarExecucaoCalculoRequest(f.competencia(),true,chave),null); }
    private long resultados(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM tb_resultado_calculo WHERE id_venda IN (?,?)",Long.class,f.primeira().getId(),f.segunda().getId()); }
    private void semResultados(Fixture f) {
        assertThat(resultados(f)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_log_calculo_imutavel WHERE id_venda IN (?,?)",Long.class,f.primeira().getId(),f.segunda().getId())).isZero();
    }
    private void await(CountDownLatch l) throws InterruptedException { assertThat(l.await(20,TimeUnit.SECONDS)).as("sinal concorrente").isTrue(); }

    @Test void idempotenciaRetornaOriginalSemEnfileirarOutraVezERecusaOutroPedido() {
        Fixture f=fixture(); var a=solicitar(f,"original"); var b=solicitar(f,"original");
        assertThat(b.id()).isEqualTo(a.id()); assertThat(fila).hasSize(1);
        assertThatThrownBy(() -> service.solicitar(new SolicitarExecucaoCalculoRequest(f.competencia(),false,"original"),null)).isInstanceOf(ResourceConflictException.class);
        Fixture outro=fixture(); assertThatThrownBy(() -> solicitar(outro,"original")).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> solicitar(f,"outra-chave")).isInstanceOf(ResourceConflictException.class);
        fila.remove().run(); var concluida=service.consultar(a.id());
        assertThat(concluida.status()).isEqualTo(StatusExecucaoCalculo.CONCLUIDA); assertThat(concluida.percentual()).isEqualTo(100);
        assertThat(concluida.resultado()).contains("vendas=2","sucessos=2"); assertThat(resultados(f)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sum(valor_comissao) FROM tb_resultado_calculo WHERE id_venda IN (?,?)",BigDecimal.class,f.primeira().getId(),f.segunda().getId())).isEqualByComparingTo("15");
        assertThatThrownBy(() -> service.retomar(a.id())).isInstanceOf(ResourceConflictException.class);
    }

    enum Mudanca { VALOR, CANAL, LOJA, MATRICULA, CARGO, MARCA, ADMISSAO, BASE, FILTRO, LIMITE, INCLUSIVIDADE, BONUS, OPERACAO, VIGENTE }
    @ParameterizedTest @EnumSource(Mudanca.class)
    void mudancaEntrePedidoEExecucaoFalhaSemResultadosParciais(Mudanca change) {
        Fixture f=fixture(); var exec=solicitar(f,"change-"+change);
        switch(change) {
            case VALOR -> { f.primeira().setValue(new BigDecimal("150")); vendas.saveAndFlush(f.primeira()); }
            case CANAL -> { f.primeira().setSaleChannel("WHATSAPP"); vendas.saveAndFlush(f.primeira()); }
            case LOJA -> { f.loja().setCode(500000+f.loja().getCode()); lojas.saveAndFlush(f.loja()); }
            case MATRICULA -> { f.pessoa().setRegistration(f.pessoa().getRegistration()+"-nova"); pessoas.saveAndFlush(f.pessoa()); }
            case CARGO -> { f.cargo().setCode(500000+f.cargo().getCode()); cargos.saveAndFlush(f.cargo()); }
            case MARCA -> { f.marca().setDescription("Outra marca"); marcas.saveAndFlush(f.marca()); }
            case ADMISSAO -> { f.pessoa().setAdmissDate(LocalDate.of(2041,1,1)); pessoas.saveAndFlush(f.pessoa()); }
            case BASE -> { f.base().setPercentage(new BigDecimal("0.03")); bases.saveAndFlush(f.base()); }
            case FILTRO -> { f.regra().setMatricula("OUTRA"); regras.saveAndFlush(f.regra()); }
            case LIMITE -> { f.regra().setValorMaximo(new BigDecimal("90")); regras.saveAndFlush(f.regra()); }
            case INCLUSIVIDADE -> { f.regra().setMinInclusivo(false); regras.saveAndFlush(f.regra()); }
            case BONUS -> { f.regra().setBonusValorFixo(new BigDecimal("10")); regras.saveAndFlush(f.regra()); }
            case OPERACAO -> { f.regra().setValorAjuste(new BigDecimal("0.01")); regras.saveAndFlush(f.regra()); }
            case VIGENTE -> {
                CampanhaVersao v=new CampanhaVersao(); v.setCampanha(f.campanha()); v.setNumero(2); v.setStatus(StatusVersaoCampanha.PUBLICADA); v.setTitulo("Nova versão"); v.setTextoOriginal("Texto"); v.setDataInicio(f.campanha().getDataInicio()); v.setDataFim(f.campanha().getDataFim()); v=versoes.saveAndFlush(v);
                f.campanha().setVersaoVigente(v); campanhas.saveAndFlush(f.campanha());
            }
        }
        fila.remove().run(); var failed=service.consultar(exec.id());
        assertThat(failed.status()).isEqualTo(StatusExecucaoCalculo.FALHOU); assertThat(failed.falha()).contains("alterados"); semResultados(f);
    }

    @Test void outroCalculoAntesDoWorkerInvalidaReferenciaSemSobrescreverResultados() {
        Fixture f=fixture(); var exec=solicitar(f,"changed-cache");
        calculo.calcularPorCompetencia(f.competencia(),false);
        assertThat(resultados(f)).isEqualTo(2);
        var antes=jdbc.queryForList("SELECT protocolo_calculo FROM tb_resultado_calculo WHERE id_venda IN (?,?) ORDER BY id_venda",f.primeira().getId(),f.segunda().getId());
        fila.remove().run(); assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.FALHOU);
        assertThat(jdbc.queryForList("SELECT protocolo_calculo FROM tb_resultado_calculo WHERE id_venda IN (?,?) ORDER BY id_venda",f.primeira().getId(),f.segunda().getId())).isEqualTo(antes);
    }

    @Test void mudancaEmResultadoReaproveitavelTambemInvalidaReferencia() {
        Fixture f=fixture(); calculo.calcularPorCompetencia(f.competencia(),false);
        var exec=solicitar(f,"changed-existing-result");
        jdbc.update("UPDATE tb_resultado_calculo SET matricula='OUTRA' WHERE id_venda=?",f.primeira().getId());
        fila.remove().run(); assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.FALHOU);
        assertThat(jdbc.queryForObject("SELECT matricula FROM tb_resultado_calculo WHERE id_venda=?",String.class,f.primeira().getId())).isEqualTo("OUTRA");
        assertThat(resultados(f)).isEqualTo(2);
    }

    @Autowired org.springframework.web.context.WebApplicationContext web;
    @Test void rotasAceitamPedidoConsultamEstadoEValidamIdentificacao() throws Exception {
        Fixture f=fixture(); var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
        String payload="{\"competencia\":\""+f.competencia()+"\",\"recalcular\":true,\"chaveIdempotencia\":\"body-key\"}";
        String response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/execucoes/calculo")
            .header("Idempotency-Key","header-key").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(payload))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted()).andReturn().getResponse().getContentAsString();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/execucoes/calculo/"+id))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("SOLICITADA"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/execucoes/calculo/"+id+"/retomar"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/execucoes/calculo/"+UUID.randomUUID()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/execucoes/calculo/invalido"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/execucoes/calculo").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"competencia\":\"2050-99\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertThat(execucoes.findByChaveIdempotencia("header-key")).isPresent();
        assertThat(execucoes.findByChaveIdempotencia("body-key")).isEmpty();
    }

    @Test void hashCanonicoIgnoraEscalaDecimalEMetadadoSemEfeitoNoCalculo() {
        Fixture f=fixture(); var before=referencias.validar(f.competencia());
        f.base().setPercentage(new BigDecimal("0.0250000")); bases.saveAndFlush(f.base());
        f.regra().setExplicacao("Explicação corrigida sem alterar os campos"); regras.saveAndFlush(f.regra());
        assertThat(referencias.validar(f.competencia())).isEqualTo(before);
    }

    @Test void retomadaAtualizaReferenciasETentativaEIgnoraCallbacksAntigos() {
        Fixture f=fixture(); var exec=solicitar(f,"retry"); UUID antiga=execucoes.findById(exec.id()).orElseThrow().getTentativa();
        f.base().setPercentage(new BigDecimal("0.03")); bases.saveAndFlush(f.base()); fila.remove().run();
        assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.FALHOU);
        var resumed=service.retomar(exec.id()); UUID nova=execucoes.findById(exec.id()).orElseThrow().getTentativa();
        assertThat(nova).isNotEqualTo(antiga); assertThat(resumed.referenciaDados()).isNotEqualTo(exec.referenciaDados());
        worker.falhar(exec.id(),antiga,"Erro atrasado"); worker.processar(exec.id(),antiga);
        assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.SOLICITADA);
        fila.remove().run(); assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.CONCLUIDA);
    }

    @Test void retomadasConcorrentesEnfileiramSomenteUmaTentativaNova() throws Exception {
        Fixture f=fixture(); doThrow(new TaskRejectedException("Fila cheia")).when(executor).execute(any(Runnable.class));
        var exec=solicitar(f,"retry-concurrent");
        doAnswer(i -> { fila.add(i.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try (var threads=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> request=() -> { ready.countDown(); await(start); try { service.retomar(exec.id()); return true; } catch (ResourceConflictException e) { return false; } };
            var a=threads.submit(request); var b=threads.submit(request); try { await(ready); } finally { start.countDown(); }
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(fila).hasSize(1);
    }

    @Test void propostaEmEdicaoEBaseDeOutroPublicoNaoInvalidamAVersaoVigente() {
        Fixture f=fixture(), outro=fixture(); var exec=solicitar(f,"unrelated-change");
        outro.base().setPercentage(new BigDecimal("0.04")); bases.saveAndFlush(outro.base());
        CampanhaVersao v=new CampanhaVersao(); v.setCampanha(f.campanha()); v.setNumero(2); v.setStatus(StatusVersaoCampanha.PROPOSTA); v.setTitulo("Proposta separada"); v.setTextoOriginal("Outra taxa"); v.setDataInicio(f.campanha().getDataInicio()); v.setDataFim(f.campanha().getDataFim()); v=versoes.saveAndFlush(v);
        f.campanha().setVersaoProposta(v); campanhas.saveAndFlush(f.campanha());
        fila.remove().run(); assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.CONCLUIDA);
        assertThat(jdbc.queryForObject("SELECT sum(valor_comissao) FROM tb_resultado_calculo WHERE id_venda IN (?,?)",BigDecimal.class,f.primeira().getId(),f.segunda().getId())).isEqualByComparingTo("15");
    }

    @Test void rejeicaoDaFilaFicaRegistradaSemInformarSucesso() {
        Fixture f=fixture(); doThrow(new TaskRejectedException("Fila cheia")).when(executor).execute(any(Runnable.class));
        var response=solicitar(f,"reject"); assertThat(response.status()).isEqualTo(StatusExecucaoCalculo.FALHOU);
        assertThat(service.consultar(response.id()).falha()).contains("Fila"); semResultados(f);
    }

    @ParameterizedTest @EnumSource(value=StatusFechamento.class,names={"PROCESSANDO","CONCLUIDO"})
    void fechamentoEntreEnfileiramentoEExecucaoBloqueiaCalculoERetomada(StatusFechamento status) {
        Fixture f=fixture(); var exec=solicitar(f,"closed-"+status);
        var closing=new FechamentoCompetencia(f.competencia(),UUID.randomUUID(),"Teste","Fechado"); closing.setStatus(status); fechamentos.saveAndFlush(closing);
        fila.remove().run(); assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.FALHOU); semResultados(f);
        assertThatThrownBy(() -> service.retomar(exec.id())).isInstanceOf(BusinessException.class).hasMessageContaining("fechamento final");
        assertThatThrownBy(() -> solicitar(f,"new-after-close")).isInstanceOf(BusinessException.class).hasMessageContaining("fechamento final");
    }

    @Test void pedidosConcorrentesDaMesmaChaveCompartilhamExecucao() throws Exception {
        Fixture f=fixture(); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try (var threads=Executors.newFixedThreadPool(2)) {
            Callable<ExecucaoCalculoResponse> request=() -> { ready.countDown(); await(start); return solicitar(f,"same-concurrent"); };
            var a=threads.submit(request); var b=threads.submit(request);
            try { await(ready); } finally { start.countDown(); }
            assertThat(a.get(30,TimeUnit.SECONDS).id()).isEqualTo(b.get(30,TimeUnit.SECONDS).id());
        }
        assertThat(fila).hasSize(1);
    }

    @Test void chaveConcorrenteEmCompetenciasDiferentesRetornaConflitoSemTransacaoAbortada() throws Exception {
        Fixture a=fixture(), b=fixture(); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try (var threads=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> first=() -> { ready.countDown(); await(start); try { solicitar(a,"cross-month"); return true; } catch (ResourceConflictException e) { return false; } };
            Callable<Boolean> second=() -> { ready.countDown(); await(start); try { solicitar(b,"cross-month"); return true; } catch (ResourceConflictException e) { return false; } };
            var x=threads.submit(first); var y=threads.submit(second); try { await(ready); } finally { start.countDown(); }
            assertThat(List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(fila).hasSize(1);
    }

    @Test void progressoFicaVisivelSemConfirmarResultadosEAFalhaReverteOCalculo() throws Exception {
        Fixture f=fixture(); var exec=solicitar(f,"progress"); var progress=new CountDownLatch(1); var release=new CountDownLatch(1); var once=new AtomicBoolean();
        doAnswer(inv -> {
            inv.callRealMethod();
            if (once.compareAndSet(false,true)) { progress.countDown(); await(release); }
            return null;
        }).when(acompanhamento).registrar(eq(exec.id()),any(),anyInt());
        doAnswer(inv -> { inv.callRealMethod(); throw new BusinessException("Falha depois de calcular"); })
            .when(calculo).calcularPorCompetenciaComProgresso(eq(f.competencia()),eq(true),any());
        Runnable run=fila.remove();
        try (var thread=Executors.newSingleThreadExecutor()) {
            var running=thread.submit(run);
            try {
                assertThat(progress.await(20,TimeUnit.SECONDS)).withFailMessage("Estado observado: %s",service.consultar(exec.id())).isTrue();
                var state=service.consultar(exec.id());
                assertThat(state.status()).isEqualTo(StatusExecucaoCalculo.PROCESSANDO); assertThat(state.itensProcessados()).isEqualTo(1);
                assertThat(state.percentual()).isEqualTo(50); semResultados(f);
                assertThatThrownBy(() -> solicitar(f,"while-processing")).isInstanceOf(ResourceConflictException.class);
            } finally { release.countDown(); }
            running.get(30,TimeUnit.SECONDS);
        }
        assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.FALHOU); semResultados(f);
        doCallRealMethod().when(calculo).calcularPorCompetenciaComProgresso(eq(f.competencia()),eq(true),any());
        service.retomar(exec.id()); fila.remove().run(); assertThat(service.consultar(exec.id()).percentual()).isEqualTo(100);
    }

    @Test void executorPodeComecarAntesDaRespostaPorqueOPedidoJaEstaConfirmado() {
        Fixture f=fixture();
        doAnswer(inv -> { inv.getArgument(0,Runnable.class).run(); return null; }).when(executor).execute(any(Runnable.class));
        var response=solicitar(f,"immediate-start");
        assertThat(service.consultar(response.id()).status()).isEqualTo(StatusExecucaoCalculo.CONCLUIDA);
        assertThat(resultados(f)).isEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="EXECUCAO_TEST_DB_URL",matches="jdbc:postgresql:.*")
    void indicePostgresImpedeDuasExecucoesAtivasMasPermiteHistorico() {
        Fixture f=fixture(); var first=solicitar(f,"index-first");
        ExecucaoCalculo another=new ExecucaoCalculo(); another.setChaveIdempotencia("index-another"); another.setCompetencia(f.competencia()); another.setReferenciaDados("dados"); another.setReferenciaRegras("regras"); another.setStatus(StatusExecucaoCalculo.PROCESSANDO);
        assertThatThrownBy(() -> execucoes.saveAndFlush(another)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        fila.remove().run(); assertThat(service.consultar(first.id()).status()).isEqualTo(StatusExecucaoCalculo.CONCLUIDA);
        var next=solicitar(f,"index-next"); assertThat(next.id()).isNotEqualTo(first.id()); assertThat(fila).hasSize(1);
    }

    @Test void doisWorkersNaoCalculamOMesmoPedidoDuasVezes() throws Exception {
        Fixture f=fixture(); var exec=solicitar(f,"duplicate-worker"); Runnable run=fila.remove();
        var started=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(inv -> { started.countDown(); await(release); return inv.callRealMethod(); })
            .when(calculo).calcularPorCompetenciaComProgresso(eq(f.competencia()),eq(true),any());
        try (var threads=Executors.newFixedThreadPool(2)) {
            var first=threads.submit(run); try { await(started); var second=threads.submit(run); release.countDown(); second.get(30,TimeUnit.SECONDS); }
            finally { release.countDown(); }
            first.get(30,TimeUnit.SECONDS);
        }
        assertThat(service.consultar(exec.id()).status()).isEqualTo(StatusExecucaoCalculo.CONCLUIDA);
        verify(calculo,times(1)).calcularPorCompetenciaComProgresso(eq(f.competencia()),eq(true),any());
        assertThat(resultados(f)).isEqualTo(2);
    }
}

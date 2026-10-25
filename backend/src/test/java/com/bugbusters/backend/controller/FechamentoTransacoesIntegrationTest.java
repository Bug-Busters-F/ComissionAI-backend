package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoResponseDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.StatusFechamento;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.FechamentoCompetenciaRepository;
import com.bugbusters.backend.service.CalculoService;
import com.bugbusters.backend.service.CampanhaService;
import com.bugbusters.backend.service.FechamentoService;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="ai.service.url=http://localhost:8000")
@ActiveProfiles("test")
class FechamentoTransacoesIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String pg = System.getenv("FECHAMENTO_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> pg != null ? pg : "jdbc:h2:mem:fechamento_transactions;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        if (pg != null) {
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("FECHAMENTO_TEST_DB_USER", "postgres"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("FECHAMENTO_TEST_DB_PASSWORD", "postgres"));
            registry.add("spring.flyway.enabled", () -> true);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.batch.jdbc.initialize-schema", () -> "never");
        }
    }
    @Autowired FechamentoService fechamento;
    @Autowired FechamentoCompetenciaRepository fechamentos;
    @Autowired CampanhaService campanhas;
    @Autowired CampanhaRepository campanhaRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TravaCompetencia trava;
    @Autowired com.bugbusters.backend.repository.CampanhaVersaoRepository versoes;
    @Autowired com.bugbusters.backend.brand.BrandRepository marcas;
    @Autowired com.bugbusters.backend.position.PositionRepository cargos;
    @Autowired com.bugbusters.backend.store.StoreRepository lojas;
    @Autowired com.bugbusters.backend.registration.RegistrationRepository pessoas;
    @Autowired com.bugbusters.backend.sales.SaleRepository vendas;
    @MockitoSpyBean CalculoService calculo;

    private FechamentoRequestDTO pedido(String competencia) { return new FechamentoRequestDTO(competencia,"Auditor de teste","Conferência isolada"); }
    private void await(CountDownLatch latch) throws InterruptedException { assertThat(latch.await(15,TimeUnit.SECONDS)).as("sinal de execução concorrente").isTrue(); }
    private CampanhaRequest proposta() throws Exception {
        return new ObjectMapper().findAndRegisterModules().readValue("""
            {"titulo":"Não pode ser criada durante fechamento","textoOriginal":"Comissão 5%",
             "dataInicio":"2041-02-01","dataFim":"2041-02-28","estado":"DRAFT",
             "regras":[{"blocoId":"one","taxa":0.05,"valorAjuste":0.05,"tipoOperacao":"DEFINIR_TAXA","completa":true}]}
            """, CampanhaRequest.class);
    }

    @Test void preservaFalhaAposRollbackReverteEscritaParcialEPermiteNovaTentativa() {
        Campanha original = new Campanha("Antes da apuração","Texto",LocalDate.of(2041,1,1),LocalDate.of(2041,1,31));
        long id = campanhaRepository.saveAndFlush(original).getId();
        doAnswer(invocation -> {
            jdbc.update("UPDATE tb_campanha SET titulo='Escrita parcial' WHERE id=?",id);
            throw new BusinessException("Falha controlada depois de escrever");
        }).when(calculo).processarCompetenciaFechamento(eq("2041-01"),any(),any());
        assertThatThrownBy(() -> fechamento.executarFechamento(pedido("2041-01"))).isInstanceOf(BusinessException.class);
        assertThat(campanhaRepository.findById(id).orElseThrow().getTitulo()).isEqualTo("Antes da apuração");
        assertThat(fechamentos.findAllByCompetencia("2041-01")).singleElement().satisfies(f -> {
            assertThat(f.getStatus()).isEqualTo(StatusFechamento.FALHOU);
            assertThat(f.getMotivoFalha()).contains("Falha controlada");
            assertThat(f.getConcluidoEm()).isNotNull();
        });
        assertThat(fechamento.consultarStatusCompetencia("2041-01").bloqueada()).isFalse();
        doCallRealMethod().when(calculo).processarCompetenciaFechamento(eq("2041-01"),any(),any());
        assertThat(fechamento.executarFechamento(pedido("2041-01")).status()).isEqualTo(StatusFechamento.CONCLUIDO);
        assertThat(fechamentos.findAllByCompetencia("2041-01")).hasSize(2);
    }

    @Test void processamentoFicaVisivelEImpedeSegundoFechamentoEMutacaoSimultanea() throws Exception {
        var iniciou = new CountDownLatch(1); var liberar = new CountDownLatch(1);
        doAnswer(invocation -> { iniciou.countDown(); await(liberar); return invocation.callRealMethod(); })
            .when(calculo).processarCompetenciaFechamento(eq("2041-02"),any(),any());
        CampanhaRequest proposta = proposta();
        try (var executor = Executors.newFixedThreadPool(3)) {
            Future<FechamentoResponseDTO> primeiro = executor.submit(() -> fechamento.executarFechamento(pedido("2041-02")));
            try {
                await(iniciou);
                assertThat(fechamento.consultarStatusCompetencia("2041-02").status()).isEqualTo("PROCESSANDO");
                assertThat(fechamento.consultarStatusCompetencia("2041-02").bloqueada()).isTrue();
                var tentativas = new CountDownLatch(2);
                var segundo = executor.submit(() -> { tentativas.countDown();
                    assertThatThrownBy(() -> fechamento.executarFechamento(pedido("2041-02")))
                        .isInstanceOf(BusinessException.class).hasMessageContaining("já possui fechamento final concluído");
                });
                var edicao = executor.submit(() -> { tentativas.countDown();
                    assertThatThrownBy(() -> campanhas.criarCampanha(proposta))
                        .isInstanceOf(BusinessException.class).hasMessageContaining("fechamento final concluído");
                });
                await(tentativas); liberar.countDown();
                assertThat(primeiro.get(20,TimeUnit.SECONDS).status()).isEqualTo(StatusFechamento.CONCLUIDO);
                segundo.get(20,TimeUnit.SECONDS); edicao.get(20,TimeUnit.SECONDS);
                assertThat(fechamentos.findAllByCompetencia("2041-02")).hasSize(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_campanha WHERE titulo=?",Integer.class,proposta.titulo())).isZero();
            } finally { liberar.countDown(); }
        }
    }

    @Test void bloqueiaPeriodoVigenteFechadoMesmoQuandoAPropostaMudouParaMesAberto() throws Exception {
        Campanha campanha = new Campanha("Vigente fechada","Texto",LocalDate.of(2041,4,1),LocalDate.of(2041,4,30));
        campanha.setEstado(com.bugbusters.backend.model.EstadoCampanha.ATIVA);
        campanha = campanhaRepository.saveAndFlush(campanha);
        var vigente = new com.bugbusters.backend.model.CampanhaVersao();
        vigente.setCampanha(campanha); vigente.setNumero(1); vigente.setStatus(com.bugbusters.backend.model.StatusVersaoCampanha.PUBLICADA);
        vigente.setTitulo("Vigente"); vigente.setTextoOriginal("Texto"); vigente.setDataInicio(campanha.getDataInicio()); vigente.setDataFim(campanha.getDataFim());
        vigente = versoes.saveAndFlush(vigente);
        var proposta = new com.bugbusters.backend.model.CampanhaVersao();
        proposta.setCampanha(campanha); proposta.setNumero(2); proposta.setStatus(com.bugbusters.backend.model.StatusVersaoCampanha.PROPOSTA);
        proposta.setTitulo("Proposta em outro mês"); proposta.setTextoOriginal("Outro texto");
        proposta.setDataInicio(LocalDate.of(2041,6,1)); proposta.setDataFim(LocalDate.of(2041,6,30));
        proposta = versoes.saveAndFlush(proposta);
        campanha.setVersaoVigente(vigente); campanha.setVersaoProposta(proposta); campanhaRepository.saveAndFlush(campanha);
        fechamento.executarFechamento(pedido("2041-04"));
        long id = campanha.getId();
        CampanhaRequest base = proposta();
        CampanhaRequest alteracao = new CampanhaRequest(base.titulo(),base.textoOriginal(),LocalDate.of(2041,6,1),LocalDate.of(2041,6,30),base.regras());
        assertThatThrownBy(() -> campanhas.atualizarCampanha(id,alteracao)).isInstanceOf(BusinessException.class).hasMessageContaining("2041-04");
        assertThatThrownBy(() -> campanhas.ativarCampanha(id,1)).isInstanceOf(BusinessException.class).hasMessageContaining("2041-04");
        assertThatThrownBy(() -> campanhas.removerLogicamente(id)).isInstanceOf(BusinessException.class).hasMessageContaining("2041-04");
        assertThat(versoes.findById(proposta.getId()).orElseThrow().getTitulo()).isEqualTo("Proposta em outro mês");
    }

    @Test void naoCalculaVendaNovaEmLoteDeCompetenciaJaFechada() {
        fechamento.executarFechamento(pedido("2041-05"));
        var marca = new com.bugbusters.backend.brand.Brand(); marca.setCode(204105); marca.setDescription("Marca teste"); marca=marcas.saveAndFlush(marca);
        var cargo = new com.bugbusters.backend.position.Position(); cargo.setCode(204105); cargo.setDescription("Cargo teste"); cargo=cargos.saveAndFlush(cargo);
        var loja = new com.bugbusters.backend.store.Store(); loja.setCode(204105); loja.setDescription("Loja teste"); loja=lojas.saveAndFlush(loja);
        var pessoa = new com.bugbusters.backend.registration.Registration(); pessoa.setRegistration("CLOSING-204105"); pessoa.setStore(loja); pessoa.setPosition(cargo); pessoa.setAdmissDate(LocalDate.of(2040,1,1)); pessoa=pessoas.saveAndFlush(pessoa);
        var venda = new com.bugbusters.backend.sales.Sale(); venda.setBrand(marca); venda.setStore(loja); venda.setRegistration(pessoa); venda.setSaleDate(LocalDate.of(2041,5,10)); venda.setValue(new java.math.BigDecimal("100")); venda=vendas.saveAndFlush(venda);
        assertThatThrownBy(() -> calculo.calcularPorCompetencia("2041-05",false)).isInstanceOf(BusinessException.class).hasMessageContaining("Não é permitido calcular novas vendas");
        var avulsa = new com.bugbusters.backend.dto.calculo.CalculoComissaoRequest(venda.getId(),pessoa.getRegistration(),venda.getValue(),venda.getSaleDate(),marca.getCode(),loja.getCode(),"LOJA_FISICA");
        assertThatThrownBy(() -> calculo.calcularComissao(avulsa)).isInstanceOf(BusinessException.class).hasMessageContaining("fechamento final concluído");
        var outraData = new com.bugbusters.backend.dto.calculo.CalculoComissaoRequest(venda.getId(),pessoa.getRegistration(),venda.getValue(),LocalDate.of(2041,8,10),marca.getCode(),loja.getCode(),"LOJA_FISICA");
        assertThatThrownBy(() -> calculo.calcularComissao(outraData)).isInstanceOf(BusinessException.class).hasMessageContaining("2041-05");
        assertThat(vendas.findById(venda.getId()).orElseThrow().getSaleDate()).isEqualTo(LocalDate.of(2041,5,10));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_resultado_calculo WHERE id_venda=?",Integer.class,venda.getId())).isZero();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="FECHAMENTO_TEST_DB_URL",matches="jdbc:postgresql:.*")
    void indicePostgresImpedeDuasReservasMasPreservaTentativasComFalha() {
        var primeira = fechamentos.saveAndFlush(new com.bugbusters.backend.model.FechamentoCompetencia("2041-07",java.util.UUID.randomUUID(),"Teste","Reserva"));
        assertThatThrownBy(() -> fechamentos.saveAndFlush(new com.bugbusters.backend.model.FechamentoCompetencia("2041-07",java.util.UUID.randomUUID(),"Teste","Duplicada")))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        primeira.setStatus(StatusFechamento.FALHOU); fechamentos.saveAndFlush(primeira);
        fechamentos.saveAndFlush(new com.bugbusters.backend.model.FechamentoCompetencia("2041-07",java.util.UUID.randomUUID(),"Teste","Nova tentativa"));
        assertThat(fechamentos.findAllByCompetencia("2041-07")).hasSize(2);
    }

    @Test void inicializaTravaNovaConcorrentementeSemAbortarTransacoes() throws Exception {
        var prontos = new CountDownLatch(2); var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> tarefa = () -> {
                prontos.countDown(); await(iniciar);
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> trava.adquirirTrava("2041-03"));
                return null;
            };
            var a = executor.submit(tarefa); var b = executor.submit(tarefa);
            try { await(prontos); } finally { iniciar.countDown(); }
            a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_trava_competencia WHERE competencia='2041-03'",Integer.class)).isEqualTo(1);
    }
}

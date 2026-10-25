package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoResumoDTO;
import com.bugbusters.backend.dto.simulacao.SimulacaoVendasPaginadasDTO;
import com.bugbusters.backend.dto.simulacao.VendaSimuladaDetalheDTO;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.Simulacao;
import com.bugbusters.backend.model.SimulacaoVenda;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.repository.SimulacaoRepository;
import com.bugbusters.backend.repository.SimulacaoVendaRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulacaoPersistenciaPaginacaoTest {

    @Autowired
    private SimulacaoCenariosService service;

    @Autowired
    private SimulacaoRepository simulacaoRepository;

    @Autowired
    private SimulacaoVendaRepository simulacaoVendaRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private LogCalculoRepository logCalculoRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;
    private BaseComiss baseComiss;
    private Campanha campanha;
    private Sale sale1;
    private Sale sale2;

    @BeforeEach
    void setUp() {
        brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });

        store = storeRepository.findByCode(75).orElseGet(() -> {
            Store s = new Store();
            s.setCode(75);
            s.setDescription("LOJA CENTRO");
            return storeRepository.save(s);
        });

        position = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR");
            return positionRepository.save(p);
        });

        registration = registrationRepository.findByRegistration("MATRIC-999").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-999");
            r.setPosition(position);
            r.setStore(store);
            r.setAdmissDate(LocalDate.of(2020, 1, 1));
            return registrationRepository.save(r);
        });

        LocalDate mesRef = LocalDate.of(2026, 8, 1);
        baseComiss = baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(brand.getId(), position.getId(), mesRef)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.0300"));
                    bc.setReferenceMonth(mesRef);
                    return baseComissRepository.save(bc);
                });

        Campanha c = new Campanha();
        c.setTitulo("Campanha Teste Persistência");
        c.setTextoOriginal("Campanha para teste BACK-7");
        c.setDataInicio(LocalDate.of(2026, 11, 1));
        c.setDataFim(LocalDate.of(2026, 11, 15));
        campanha = campanhaRepository.save(c);

        // Venda 1: R$ 5.000,00 no canal ECOMMERCE
        sale1 = new Sale();
        sale1.setBrand(brand);
        sale1.setStore(store);
        sale1.setRegistration(registration);
        sale1.setSaleChannel("ECOMMERCE");
        sale1.setSaleDate(LocalDate.of(2026, 8, 10));
        sale1.setValue(new BigDecimal("5000.00"));
        saleRepository.save(sale1);

        // Venda 2: R$ 10.000,00 no canal ECOMMERCE
        sale2 = new Sale();
        sale2.setBrand(brand);
        sale2.setStore(store);
        sale2.setRegistration(registration);
        sale2.setSaleChannel("ECOMMERCE");
        sale2.setSaleDate(LocalDate.of(2026, 8, 15));
        sale2.setValue(new BigDecimal("10000.00"));
        saleRepository.save(sale2);
    }

    private RegraItemRequest criarRegra(String nome, BigDecimal taxa, BigDecimal vMin, BigDecimal vMax) {
        return new RegraItemRequest(
                "bloco-1", "regra", nome, "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                vMin, vMin != null, vMax, vMax != null,
                TipoOperacaoBase.DEFINIR_TAXA, taxa,
                null, null, null, taxa,
                null, null, null, true, null,
                null, null
        );
    }

    private SimulacaoCenariosRequest criarRequest(Long campanhaId, List<RegraItemRequest> regras) {
        return new SimulacaoCenariosRequest(
                "2026-08",
                campanhaId,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 15),
                regras,
                50,
                0,
                false,
                false
        );
    }

    @Test
    @DisplayName("Deve simular, persistir em tb_simulacao e tb_simulacao_venda e garantir isolamento de produção")
    void deveSimularEPersistirComIsolamento() {
        long contagemResultadosAntes = resultadoCalculoRepository.count();
        long contagemLogsAntes = logCalculoRepository.count();

        RegraItemRequest regra = criarRegra("Regra E-commerce 5%", new BigDecimal("0.0500"), null, null);
        SimulacaoCenariosRequest request = criarRequest(campanha.getId(), List.of(regra));

        SimulacaoCenariosResponse response = service.simularEPersistir(request);

        // 1. Asserções do cabeçalho da resposta
        assertThat(response.id()).isNotNull();
        assertThat(response.protocolo()).isNotNull();
        assertThat(response.competencia()).isEqualTo("2026-08");
        assertThat(response.totalVendasRecortadas()).isEqualTo(2);
        assertThat(response.valorTotalRecortado()).isEqualByComparingTo("15000.00");
        assertThat(response.diasInclusivos()).isEqualTo(15);
        assertThat(response.fatorProjecao()).isEqualByComparingTo("0.500000");

        // 2. Isolamento estrito de produção
        assertThat(resultadoCalculoRepository.count()).isEqualTo(contagemResultadosAntes);
        assertThat(logCalculoRepository.count()).isEqualTo(contagemLogsAntes);

        // 3. Verificação no banco de dados isolado (tb_simulacao)
        Simulacao simulacaoNoBanco = simulacaoRepository.findById(response.id()).orElseThrow();
        assertThat(simulacaoNoBanco.getProtocolo()).isEqualTo(response.protocolo());
        assertThat(simulacaoNoBanco.getCompetencia()).isEqualTo("2026-08");
        assertThat(simulacaoNoBanco.getTituloCampanha()).isEqualTo("Campanha Teste Persistência");
        assertThat(simulacaoNoBanco.getTotalVendasRecortadas()).isEqualTo(2);
        assertThat(simulacaoNoBanco.getMemoriaCalculoConsolidada()).isNotBlank();
        assertThat(simulacaoNoBanco.getMemoriaCalculoConsolidada())
                .contains("Simulação consolidada para a competência 2026-08")
                .contains("Cenário 100%")
                .contains("Sensibilidade Financeira");

        // 4. Verificação no banco de dados de vendas (tb_simulacao_venda)
        long countVendas = simulacaoVendaRepository.countBySimulacaoId(response.id());
        assertThat(countVendas).isEqualTo(2);

        SimulacaoVenda sv1 = simulacaoVendaRepository.findBySimulacaoIdAndIdVenda(response.id(), sale1.getId()).orElseThrow();
        assertThat(sv1.getValorVendaOriginal()).isEqualByComparingTo("5000.00");
        assertThat(sv1.getTaxaBaseContratual()).isEqualByComparingTo("0.0300");
        assertThat(sv1.getValorComissaoBase()).isEqualByComparingTo("150.00");
        assertThat(sv1.getTaxaAplicada100()).isEqualByComparingTo("0.0500");
        assertThat(sv1.getValorComissao100()).isEqualByComparingTo("250.00");
        assertThat(sv1.getDeltaComissao100()).isEqualByComparingTo("100.00");
        assertThat(sv1.isValida()).isTrue();
        assertThat(sv1.getMemoriaCalculoItem()).isNotBlank();
        assertThat(sv1.getMemoriaCalculoItem())
                .contains("Valor Bruto Original: R$ 5.000,00")
                .contains("Taxa Base Contratual (tb_basecomiss): 3,00%")
                .contains("Regra E-commerce 5%")
                .contains("Comissão Proposta: R$ 250,00");
    }

    @Test
    @DisplayName("Deve consultar vendas paginadas com totais globais independentes da página")
    void deveConsultarVendasPaginadasComTotaisGlobaisIndependentes() {
        RegraItemRequest regra = criarRegra("Regra E-commerce 5%", new BigDecimal("0.0500"), null, null);
        SimulacaoCenariosRequest request = criarRequest(campanha.getId(), List.of(regra));

        SimulacaoCenariosResponse simResp = service.simularEPersistir(request);
        UUID simulacaoId = simResp.id();

        // Consulta Página 0 com tamanho 1
        SimulacaoVendasPaginadasDTO pagina0 = service.listarVendasPaginadas(
                simulacaoId, null, null, null, PageRequest.of(0, 1)
        );

        // Consulta Página 1 com tamanho 1
        SimulacaoVendasPaginadasDTO pagina1 = service.listarVendasPaginadas(
                simulacaoId, null, null, null, PageRequest.of(1, 1)
        );

        assertThat(pagina0.totalElementos()).isEqualTo(2);
        assertThat(pagina0.totalPaginas()).isEqualTo(2);
        assertThat(pagina0.vendas()).hasSize(1);

        assertThat(pagina1.totalElementos()).isEqualTo(2);
        assertThat(pagina1.totalPaginas()).isEqualTo(2);
        assertThat(pagina1.vendas()).hasSize(1);

        // TOTAIS GLOBAIS INDEPENDENTES DA PÁGINA:
        assertThat(pagina0.cenario100().totalVendas()).isEqualByComparingTo("15000.00");
        assertThat(pagina1.cenario100().totalVendas()).isEqualByComparingTo("15000.00");

        assertThat(pagina0.cenario100().totalComissaoProposta()).isEqualByComparingTo("750.00");
        assertThat(pagina1.cenario100().totalComissaoProposta()).isEqualByComparingTo("750.00");

        assertThat(pagina0.cenario100().deltaComissao()).isEqualByComparingTo("300.00");
        assertThat(pagina1.cenario100().deltaComissao()).isEqualByComparingTo("300.00");

        assertThat(pagina0.vendas().get(0).id()).isNotEqualTo(pagina1.vendas().get(0).id());
    }

    @Test
    @DisplayName("Deve buscar detalhe e rastreabilidade explicativa da venda individual sem chamadas à IA")
    void deveBuscarDetalheERastreabilidadeVendaIndividual() {
        RegraItemRequest regra = criarRegra("Regra Promocional 4%", new BigDecimal("0.0400"), null, null);
        SimulacaoCenariosRequest request = criarRequest(campanha.getId(), List.of(regra));

        SimulacaoCenariosResponse simResp = service.simularEPersistir(request);

        VendaSimuladaDetalheDTO detalheVenda1 = service.buscarVendaSimuladaDetalhe(simResp.id(), sale1.getId());
        assertThat(detalheVenda1).isNotNull();
        assertThat(detalheVenda1.id()).isEqualTo(sale1.getId());
        assertThat(detalheVenda1.matricula()).isEqualTo("MATRIC-999");
        assertThat(detalheVenda1.valorVendaOriginal()).isEqualByComparingTo("5000.00");
        assertThat(detalheVenda1.taxaBaseContratual()).isEqualByComparingTo("0.0300");
        assertThat(detalheVenda1.cenario100().taxaAplicada()).isEqualByComparingTo("0.0400");
        assertThat(detalheVenda1.cenario100().valorComissao()).isEqualByComparingTo("200.00");
        assertThat(detalheVenda1.deltaComissao100()).isEqualByComparingTo("50.00");
        assertThat(detalheVenda1.memoriaCalculo()).isNotBlank();
        assertThat(detalheVenda1.memoriaCalculo())
                .contains("Venda ID " + sale1.getId())
                .contains("Taxa Base Contratual (tb_basecomiss): 3,00%")
                .contains("Regra Promocional 4%")
                .contains("Comissão Proposta: R$ 200,00");
    }

    @Test
    @DisplayName("Deve listar histórico de simulações com paginação e filtro por competência")
    void deveListarHistoricoSimulacoesPaginado() {
        RegraItemRequest regra = criarRegra("Regra Padrão", new BigDecimal("0.0400"), null, null);
        SimulacaoCenariosRequest request = criarRequest(campanha.getId(), List.of(regra));

        service.simularEPersistir(request);

        Page<SimulacaoResumoDTO> pagina = service.listarSimulacoes("2026-08", null, PageRequest.of(0, 10));
        assertThat(pagina.getTotalElements()).isGreaterThanOrEqualTo(1);
        SimulacaoResumoDTO resumo = pagina.getContent().get(0);
        assertThat(resumo.competencia()).isEqualTo("2026-08");
        assertThat(resumo.totalVendasRecortadas()).isEqualTo(2);
        assertThat(resumo.cenario100()).isNotNull();
        assertThat(resumo.cenario80()).isNotNull();
        assertThat(resumo.cenario120()).isNotNull();
        assertThat(resumo.cenario150()).isNotNull();
    }
}

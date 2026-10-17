package com.bugbusters.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.calculo.CalculoIndividualResponseDTO;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Casos de ouro (S2 §11.3): executa golden/golden_cases.json contra o motor real (CalculoService,
 * TaxaComissaoResolver, repositórios em H2). O mesmo arquivo é usado nos testes do serviço de IA.
 * Cada caso roda em transação revertida ao final, com regras e bases pré-existentes neutralizadas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoldenCasesTest {

    @Autowired private CalculoService calculoService;
    @Autowired private BrandRepository brandRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private BaseComissRepository baseComissRepository;
    @Autowired private RegraRepository regraRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ResultadoCalculoRepository resultadoCalculoRepository;

    @SuppressWarnings("unchecked")
    static Stream<Arguments> casos() throws Exception {
        try (InputStream in = GoldenCasesTest.class.getResourceAsStream("/golden/golden_cases.json")) {
            assertNotNull(in, "golden_cases.json não encontrado em src/test/resources/golden");
            Map<String, Object> doc = new JsonMapper().readValue(in, Map.class);
            List<Map<String, Object>> casos = (List<Map<String, Object>>) doc.get("casos");
            return casos.stream().map(c -> Arguments.of(c.get("id"), c));
        }
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    @DisplayName("Caso de ouro")
    void deveReproduzirCasoDeOuro(String id, Map<String, Object> caso) {
        Map<String, Object> v = (Map<String, Object>) caso.get("venda");
        Map<String, Object> esperado = (Map<String, Object>) caso.get("esperado");
        LocalDate dataVenda = LocalDate.parse((String) v.get("dataVenda"));

        // Neutraliza dados de outros testes (tudo é revertido ao final da transação).
        List<Regra> existentes = regraRepository.findAll();
        existentes.forEach(r -> r.setStatus(StatusRegra.INATIVA));
        regraRepository.saveAll(existentes);
        baseComissRepository.deleteAll();
        regraRepository.flush();

        Brand brand = brandRepository.findByCode((Integer) v.get("codMarca")).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode((Integer) v.get("codMarca"));
            b.setDescription("MARCA " + v.get("codMarca"));
            return brandRepository.save(b);
        });
        Store store = storeRepository.findByCode((Integer) v.get("codLoja")).orElseGet(() -> {
            Store s = new Store();
            s.setCode((Integer) v.get("codLoja"));
            s.setDescription("LOJA " + v.get("codLoja"));
            return storeRepository.save(s);
        });
        Position position = positionRepository.findByCode((Integer) v.get("codCargo")).orElseGet(() -> {
            Position p = new Position();
            p.setCode((Integer) v.get("codCargo"));
            p.setDescription("CARGO " + v.get("codCargo"));
            return positionRepository.save(p);
        });
        Registration registration = new Registration();
        registration.setRegistration((String) v.get("matricula"));
        registration.setStore(store);
        registration.setPosition(position);
        registration.setAdmissDate(LocalDate.of(2020, 1, 1));
        registration = registrationRepository.save(registration);

        Object base = caso.get("baseComiss");
        if (base != null) {
            BaseComiss bc = new BaseComiss(brand, position, new BigDecimal((String) base));
            bc.setReferenceMonth(dataVenda.withDayOfMonth(1));
            baseComissRepository.save(bc);
        }

        // Regras pertencem a uma campanha (regra sem campanha com id 1 é tratada como registro técnico).
        Campanha campanha = new Campanha();
        campanha.setTitulo("Golden " + id);
        campanha.setTextoOriginal("golden");
        campanha.setDataInicio(LocalDate.of(2026, 1, 1));
        campanha.setDataFim(LocalDate.of(2026, 12, 31));
        campanha.setEstado(EstadoCampanha.ATIVA);
        campanha = campanhaRepository.save(campanha);

        Map<String, Long> idPorRef = new HashMap<>();
        for (Map<String, Object> r : (List<Map<String, Object>>) caso.get("regras")) {
            Regra regra = new Regra();
            regra.setCampanha(campanha);
            regra.setNome("Golden " + r.get("ref"));
            regra.setCanal((String) r.get("canal"));
            regra.setCodMarca((Integer) r.get("codMarca"));
            regra.setCodLoja((Integer) r.get("codLoja"));
            regra.setCodCargo((Integer) r.get("codCargo"));
            regra.setMatricula((String) r.get("matricula"));
            regra.setTaxa(new BigDecimal((String) r.get("taxa")));
            regra.setValorMinimo(decimal(r.get("valorMin")));
            regra.setValorMaximo(decimal(r.get("valorMax")));
            regra.setMinInclusivo((Boolean) r.get("minInclusivo"));
            regra.setMaxInclusivo((Boolean) r.get("maxInclusivo"));
            regra.setBonusPercentual(decimal(r.get("bonusPercentual")));
            regra.setBonusValorFixo(decimal(r.get("bonusValorFixo")));
            regra.setDataInicio(LocalDate.parse((String) r.get("dataInicio")));
            regra.setDataFim(LocalDate.parse((String) r.get("dataFim")));
            regra.setStatus(StatusRegra.ATIVA);
            idPorRef.put((String) r.get("ref"), regraRepository.save(regra).getId());
        }
        regraRepository.flush();

        Sale sale = new Sale();
        sale.setId(UUID.randomUUID());
        sale.setRegistration(registration);
        sale.setStore(store);
        sale.setBrand(brand);
        sale.setValue(new BigDecimal((String) v.get("valor")));
        sale.setSaleDate(dataVenda);
        sale.setSaleChannel((String) v.get("canal"));
        saleRepository.saveAndFlush(sale);

        CalculoIndividualResponseDTO resposta = calculoService.calcularVendaIndividualPorId(sale.getId());

        assertEquals(esperado.get("status"), resposta.status(), "status do caso " + id);
        if ("IMPEDIDO".equals(esperado.get("status"))) {
            assertTrue(resposta.motivoImpedimento().contains((String) esperado.get("motivoContem")),
                    "motivo do impedimento: " + resposta.motivoImpedimento());
            return;
        }

        assertEquals(0, new BigDecimal((String) esperado.get("taxa")).compareTo(resposta.taxaAplicada()), "taxa");
        assertEquals(esperado.get("origemTaxa"), resposta.origemTaxa(), "origem da taxa");
        String ref = (String) esperado.get("regraAplicada");
        if (!"BASE".equals(ref)) {
            assertEquals(idPorRef.get(ref), resposta.idRegra(), "regra aplicada");
        }
        assertEquals(0, new BigDecimal((String) esperado.get("comissaoTotal")).compareTo(resposta.valorComissao()),
                "comissão total esperada " + esperado.get("comissaoTotal") + " obtida " + resposta.valorComissao());

        ResultadoCalculo resultado = resultadoCalculoRepository.findByIdVenda(sale.getId()).orElseThrow();
        BigDecimal bonusPercentual = new BigDecimal((String) esperado.get("bonusPercentual"));
        BigDecimal bonusFixo = new BigDecimal((String) esperado.get("bonusFixo"));
        BigDecimal comissaoBase = new BigDecimal((String) esperado.get("comissaoBase"));
        assertEquals(0, bonusPercentual.compareTo(resultado.getValorBonusPercentual()), "bônus percentual");
        assertEquals(0, bonusFixo.compareTo(resultado.getValorBonusFixo()), "bônus fixo");
        assertEquals(0, comissaoBase.compareTo(
                resultado.getValorComissao().subtract(resultado.getValorBonusPercentual()).subtract(resultado.getValorBonusFixo())),
                "comissão base");
    }

    private static BigDecimal decimal(Object valor) {
        return valor == null ? null : new BigDecimal((String) valor);
    }
}

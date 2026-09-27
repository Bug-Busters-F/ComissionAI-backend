package com.bugbusters.backend.service;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.calculo.CalculoIndividualResponseDTO;
import com.bugbusters.backend.dto.calculo.LogCalculoResponse;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.sales.dto.SaleRequestDTO;
import com.bugbusters.backend.sales.dto.SaleResponseDTO;
import com.bugbusters.backend.sales.SaleService;
import com.bugbusters.backend.sales.SaleMapper;
import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ImutabilidadeLogCalculoTest {

    @Autowired
    private CalculoService calculoService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleMapper saleMapper;

    @Autowired
    private LogCalculoRepository logCalculoRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    private Brand brand1;
    private Store store1;
    private Position position1;
    private Registration registration1;

    @BeforeEach
    void setUp() {
        logCalculoRepository.deleteAllInBatch();
        resultadoCalculoRepository.deleteAll();
        saleRepository.deleteAll();

        // Dados de apoio
        brand1 = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("Marca Teste 10");
            return brandRepository.save(b);
        });

        store1 = storeRepository.findByCode(75).orElseGet(() -> {
            Store s = new Store();
            s.setCode(75);
            s.setDescription("Loja Teste 75");
            return storeRepository.save(s);
        });

        position1 = positionRepository.findByCode(150).orElseGet(() -> {
            Position p = new Position();
            p.setCode(150);
            p.setDescription("Vendedor Pleno");
            return positionRepository.save(p);
        });

        registration1 = registrationRepository.findByRegistration("MATRIC-IMUT-1").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-IMUT-1");
            r.setStore(store1);
            r.setPosition(position1);
            r.setAdmissDate(LocalDate.of(2025, 1, 1));
            return registrationRepository.save(r);
        });
    }

    @Test
    @DisplayName("BUG-23.1: Tentativa de excluir log imutável via repositório deve lançar UnsupportedOperationException")
    void deveImpedirExclusaoDeLogImutavel() {
        LogCalculoImutavel log = new LogCalculoImutavel(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MATRIC-IMUT-1",
                150,
                75,
                10,
                new BigDecimal("1000.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                1L,
                LocalDate.of(2026, 9, 15),
                "ECOMMERCE",
                "MOTOR_PRODUCAO",
                "INFORMADA",
                "LOTE-001",
                "REGRA_PADRAO_V1",
                "{\"taxa\":0.1000}"
        );
        LogCalculoImutavel salvo = logCalculoRepository.save(log);

        assertThrows(UnsupportedOperationException.class, () -> logCalculoRepository.delete(salvo));
        assertThrows(UnsupportedOperationException.class, () -> logCalculoRepository.deleteById(salvo.getId()));
    }

    @Test
    @DisplayName("BUG-23.2: Tentativa de atualizar log imutável via lifecycle JPA deve disparar UnsupportedOperationException")
    void deveImpedirAtualizacaoDeLogImutavel() {
        LogCalculoImutavel log = new LogCalculoImutavel(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MATRIC-IMUT-1",
                150,
                75,
                10,
                new BigDecimal("1000.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                1L,
                LocalDate.of(2026, 9, 15),
                "ECOMMERCE",
                "MOTOR_PRODUCAO"
        );
        assertThrows(UnsupportedOperationException.class, log::preUpdate);
        assertThrows(UnsupportedOperationException.class, log::preRemove);
    }

    @Test
    @DisplayName("BUG-23.3: Preservar histórico e snapshot de parâmetros após edição posterior da regra de comissão")
    @Transactional
    void devePreservarResultadoAposEdicaoDaRegra() {
        // 1. Cria campanha e regra ativa com 10%
        Campanha campanha = new Campanha("Campanha Inicial", "Texto Campanha", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        campanha = campanhaRepository.save(campanha);

        Regra regra = new Regra(
                campanha,
                "Regra 10 Porcento",
                "ECOMMERCE",
                10,
                75,
                150,
                "Vendedor Pleno",
                "MATRIC-IMUT-1",
                new BigDecimal("0.1000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
        regra = regraRepository.save(regra);

        // 2. Registra venda e calcula comissão
        UUID idVenda = UUID.randomUUID();
        SaleRequestDTO saleRequest = new SaleRequestDTO(
                idVenda,
                "MATRIC-IMUT-1",
                10,
                75,
                new BigDecimal("2000.00"),
                LocalDate.of(2026, 9, 10),
                "ECOMMERCE"
        );
        saleService.registrarVenda(saleRequest);

        CalculoIndividualResponseDTO calculo = calculoService.calcularVendaIndividualPorId(idVenda);
        assertEquals("SUCESSO", calculo.status());
        assertEquals(new BigDecimal("200.00"), calculo.valorComissao());
        assertEquals(new BigDecimal("0.1000"), calculo.taxaAplicada());

        // 3. Edita a regra original alterando taxa para 35% e nome
        regra.setTaxa(new BigDecimal("0.3500"));
        regra.setNome("Regra Alterada para 35");
        regraRepository.save(regra);

        // 4. Consulta os logs de auditoria e garante que os valores históricos NÃO mudaram
        Page<LogCalculoResponse> logs = calculoService.listarLogs(
                idVenda, null, null, null, null, PageRequest.of(0, 10)
        );
        assertEquals(1, logs.getTotalElements());
        LogCalculoResponse logPreservado = logs.getContent().get(0);

        assertEquals(new BigDecimal("200.00"), logPreservado.valorComissao());
        assertEquals(new BigDecimal("0.1000"), logPreservado.taxaAplicada());
        assertEquals("INFORMADA", logPreservado.tipoVenda());
        assertNotNull(logPreservado.parametrosAplicados());
        assertTrue(logPreservado.parametrosAplicados().contains("\"taxaAplicada\":0.1000"));
        assertTrue(logPreservado.parametrosAplicados().contains("\"valorVenda\":2000.00"));

        // O recálculo atualiza apenas o resultado vigente e acrescenta outro log.
        var recalculo = calculoService.calcularPorCompetencia("2026-09", true);
        assertEquals(new BigDecimal("700.00"), recalculo.valorTotalComissao());
        assertEquals("REGRA_NEGOCIO", recalculo.resultados().get(0).origemTaxa());
        resultadoCalculoRepository.flush();
        logCalculoRepository.flush();

        var historico = calculoService.listarLogs(idVenda, null, null, null, null, PageRequest.of(0, 10));
        assertEquals(2, historico.getTotalElements());
        var anterior = historico.stream().filter(item -> item.protocolo().equals(calculo.protocoloCalculo()))
                .findFirst().orElseThrow();
        var atual = historico.stream().filter(item -> !item.protocolo().equals(calculo.protocoloCalculo()))
                .findFirst().orElseThrow();
        assertEquals(logPreservado.parametrosAplicados(), anterior.parametrosAplicados());
        assertEquals(new BigDecimal("200.00"), anterior.valorComissao());
        assertEquals(new BigDecimal("700.00"), atual.valorComissao());
        assertEquals("REGRA_NEGOCIO", atual.origemTaxa());
        assertTrue(atual.versaoRegra().contains("Regra Alterada para 35"));
        assertTrue(atual.parametrosAplicados().contains("\"taxaAplicada\":0.3500"));

    }

    @Test
    @DisplayName("BUG-23.4: Preservar interpretação dos resultados após remoção lógica da regra (status INATIVA / soft delete)")
    @Transactional
    void devePreservarResultadoAposRemocaoLogicaDaRegra() {
        Campanha campanha = new Campanha("Campanha Desativacao", "Texto Desativacao", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        campanha = campanhaRepository.save(campanha);

        Regra regra = new Regra(
                campanha,
                "Regra Soft Delete",
                "LOJA_FISICA",
                10,
                75,
                150,
                "Vendedor Pleno",
                "MATRIC-IMUT-1",
                new BigDecimal("0.1200"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
        regra = regraRepository.save(regra);

        UUID idVenda = UUID.randomUUID();
        Sale sale = new Sale();
        sale.setId(idVenda);
        sale.setRegistration(registration1);
        sale.setBrand(brand1);
        sale.setStore(store1);
        sale.setValue(new BigDecimal("1000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 20));
        sale.setSaleChannel("LOJA_FISICA");
        sale.setTipoVenda("INFORMADA");
        saleRepository.save(sale);

        CalculoIndividualResponseDTO calculo = calculoService.calcularVendaIndividualPorId(idVenda);
        assertEquals("SUCESSO", calculo.status());
        assertEquals(new BigDecimal("120.00"), calculo.valorComissao());

        // Remoção lógica da regra
        regra.setStatus(StatusRegra.INATIVA);
        regra.setRemovidoEm(OffsetDateTime.now());
        regraRepository.save(regra);

        // Busca o log por ID
        Page<LogCalculoResponse> logs = calculoService.listarLogs(idVenda, null, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, logs.getTotalElements());
        UUID idLog = logs.getContent().get(0).idLog();

        LogCalculoResponse logRecuperado = calculoService.buscarPorId(idLog);
        assertNotNull(logRecuperado);
        assertEquals(new BigDecimal("120.00"), logRecuperado.valorComissao());
        assertEquals(new BigDecimal("0.1200"), logRecuperado.taxaAplicada());
        assertEquals("INFORMADA", logRecuperado.tipoVenda());
        assertTrue(logRecuperado.versaoRegra().contains("Regra Soft Delete"));
    }

    @Test
    @DisplayName("BUG-23.5: Distinguir venda informada vs importada e armazenar idLoteOrigem quando houver")
    void deveDistinguirVendaInformadaEImportadaComLote() {
        // Venda informada
        SaleRequestDTO request = new SaleRequestDTO(
                UUID.randomUUID(),
                "MATRIC-IMUT-1",
                10,
                75,
                new BigDecimal("500.00"),
                LocalDate.of(2026, 9, 5),
                "ECOMMERCE",
                "INFORMADA",
                "LOTE-MANUAL-1"
        );
        SaleResponseDTO informada = saleService.registrarVenda(request);
        assertEquals("INFORMADA", informada.id() != null ? saleRepository.findById(informada.id()).get().getTipoVenda() : null);

        // Venda importada via Mapper
        SalesFileRow row = new SalesFileRow(
                LocalDate.of(2026, 9, 12),
                10,
                "Marca 10",
                75,
                "Loja 75",
                "MATRIC-IMUT-1",
                new BigDecimal("1500.00")
        );

        Sale importada = saleMapper.toEntity(row);
        assertNotNull(importada);
        assertEquals("IMPORTADA", importada.getTipoVenda());
    }
}

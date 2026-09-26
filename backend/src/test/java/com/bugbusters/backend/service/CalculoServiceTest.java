package com.bugbusters.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO;
import com.bugbusters.backend.dto.calculo.CalculoIndividualResponseDTO;
import com.bugbusters.backend.dto.calculo.LogCalculoResponse;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;

@ExtendWith(MockitoExtension.class)
class CalculoServiceTest {

    @Mock
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Mock
    private LogCalculoRepository logCalculoRepository;

    @Mock
    private RegraRepository regraRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private TaxaComissaoResolver taxaComissaoResolver;

    @InjectMocks
    private CalculoService calculoService;

    private CalculoComissaoRequest requestPadrao;
    private final UUID ID_VENDA = UUID.randomUUID();
    private final String MATRICULA = "MATRIC-123";
    private final LocalDate DATA_VENDA = LocalDate.of(2026, 10, 15);
    private final BigDecimal VALOR_VENDA = new BigDecimal("1000.00");

    @BeforeEach
    void setUp() {
        lenient().when(regraRepository.existsById(anyLong())).thenReturn(true);

        requestPadrao = new CalculoComissaoRequest(
                ID_VENDA,
                MATRICULA,
                VALOR_VENDA,
                DATA_VENDA,
                10,
                "PRETO",
                75,
                "ECOMMERCE"
        );
    }

    @Test
    @DisplayName("1. Deve calcular comissão na primeira vez e gerar exatamente 1 log imutável")
    void deveCalcularComissaoPrimeiraVezEGerarLog() {
        when(resultadoCalculoRepository.findByIdVenda(ID_VENDA))
                .thenReturn(Optional.empty());
        when(resultadoCalculoRepository.findByMatriculaAndDataVendaAndRegraId(MATRICULA, DATA_VENDA, 1L))
                .thenReturn(Optional.empty());

        when(resultadoCalculoRepository.save(any(ResultadoCalculo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CalculoComissaoResponse response = calculoService.calcularComissao(requestPadrao);

        assertNotNull(response);
        assertNotNull(response.protocoloCalculo());
        assertEquals(MATRICULA, response.matricula());
        assertEquals(VALOR_VENDA, response.valorOriginal());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());
        assertEquals(new BigDecimal("0.1000"), response.taxaAplicada());

        verify(resultadoCalculoRepository, times(1)).save(any(ResultadoCalculo.class));
        verify(logCalculoRepository, times(1)).save(any(LogCalculoImutavel.class));
    }

    @Test
    @DisplayName("2. Deve retornar resultado pré-existente e NÃO gerar novo log quando houver reenvio com mesmos dados (Idempotência)")
    void deveRetornarResultadoExistenteSemGerarNovoLogQuandoReenvioIdentico() {
        UUID protocoloOriginal = UUID.randomUUID();
        ResultadoCalculo calculoExistente = new ResultadoCalculo(
                protocoloOriginal,
                ID_VENDA,
                MATRICULA,
                10,
                75,
                null,
                1L,
                DATA_VENDA,
                VALOR_VENDA,
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                "INDIVIDUAL"
        );

        when(resultadoCalculoRepository.findByIdVenda(ID_VENDA))
                .thenReturn(Optional.of(calculoExistente));

        CalculoComissaoResponse response = calculoService.calcularComissao(requestPadrao);

        assertNotNull(response);
        assertEquals(protocoloOriginal, response.protocoloCalculo());
        assertEquals(MATRICULA, response.matricula());
        assertEquals(VALOR_VENDA, response.valorOriginal());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());

        verify(resultadoCalculoRepository, never()).save(any());
        verify(logCalculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Deve rejeitar solicitação com BusinessException quando houver reaproveitamento com dados divergentes")
    void deveRejeitarReenvioComDadosDivergentes() {
        ResultadoCalculo calculoExistente = new ResultadoCalculo(
                UUID.randomUUID(),
                ID_VENDA,
                MATRICULA,
                10,
                75,
                null,
                1L,
                DATA_VENDA,
                new BigDecimal("1000.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                "INDIVIDUAL"
        );

        when(resultadoCalculoRepository.findByIdVenda(ID_VENDA))
                .thenReturn(Optional.of(calculoExistente));

        CalculoComissaoRequest requestDivergente = new CalculoComissaoRequest(
                ID_VENDA,
                MATRICULA,
                new BigDecimal("1500.00"),
                DATA_VENDA,
                10,
                "PRETO",
                75,
                "ECOMMERCE"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                calculoService.calcularComissao(requestDivergente)
        );

        assertTrue(ex.getMessage().contains("dados divergentes"));
        assertTrue(ex.getMessage().contains("1000.00"));
        assertTrue(ex.getMessage().contains("1500.00"));
        assertTrue(ex.getMessage().contains(ID_VENDA.toString()));

        verify(resultadoCalculoRepository, never()).save(any());
        verify(logCalculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Deve tratar concorrência via DataIntegrityViolationException recuperando o cálculo vencedor")
    void deveTratarConcorrenciaQuandoOcorreDataIntegrityViolation() {
        when(resultadoCalculoRepository.findByIdVenda(ID_VENDA))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ResultadoCalculo(
                        UUID.randomUUID(),
                        ID_VENDA,
                        MATRICULA,
                        10,
                        75,
                        null,
                        1L,
                        DATA_VENDA,
                        VALOR_VENDA,
                        new BigDecimal("0.1000"),
                        new BigDecimal("100.00"),
                        "INDIVIDUAL"
                )));

        when(resultadoCalculoRepository.findByMatriculaAndDataVendaAndRegraId(MATRICULA, DATA_VENDA, 1L))
                .thenReturn(Optional.empty());

        when(resultadoCalculoRepository.save(any(ResultadoCalculo.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key error"));

        CalculoComissaoResponse response = calculoService.calcularComissao(requestPadrao);

        assertNotNull(response);
        assertEquals(MATRICULA, response.matricula());
        assertEquals(VALOR_VENDA, response.valorOriginal());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());

        verify(logCalculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. Deve listar logs reais quando existirem registros")
    void deveListarLogsExistentes() {
        LogCalculoImutavel log = new LogCalculoImutavel(
                UUID.randomUUID(),
                ID_VENDA,
                MATRICULA,
                null,
                75,
                10,
                VALOR_VENDA,
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                1L,
                DATA_VENDA,
                "ECOMMERCE",
                "MOTOR_PRODUCAO"
        );

        when(logCalculoRepository.findAllByOrderByExecutadoEmDesc()).thenReturn(List.of(log));

        List<LogCalculoResponse> logs = calculoService.listarLogs();

        assertEquals(1, logs.size());
        assertEquals(MATRICULA, logs.get(0).matricula());
        assertEquals(new BigDecimal("100.00"), logs.get(0).valorComissao());
    }

    // ==========================================
    // Testes de Cálculo Individual (S1-B07)
    // ==========================================
    @Test
    @DisplayName("6. Deve calcular venda individual por ID com taxa básica 0.10 resultando em R$ 100 para R$ 1.000")
    void deveCalcularVendaIndividualPorIdComSucesso() {
        UUID id = UUID.randomUUID();
        Sale sale = new Sale();
        sale.setId(id);
        sale.setValue(new BigDecimal("1000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setSaleChannel("LOJA_FISICA");

        Brand brand = new Brand();
        brand.setCode(10);
        sale.setBrand(brand);

        Store store = new Store();
        store.setCode(75);
        sale.setStore(store);

        Position pos = new Position();
        pos.setCode(100);

        Registration reg = new Registration();
        reg.setRegistration("MATRIC-1");
        reg.setPosition(pos);
        sale.setRegistration(reg);

        when(saleRepository.findById(id)).thenReturn(Optional.of(sale));
        when(resultadoCalculoRepository.findByIdVenda(id)).thenReturn(Optional.empty());
        when(taxaComissaoResolver.resolverTaxa(sale))
                .thenReturn(ResolucaoTaxaResult.sucesso(new BigDecimal("0.1000"), 1L, "BASE_COMISS"));

        when(resultadoCalculoRepository.save(any(ResultadoCalculo.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CalculoIndividualResponseDTO response = calculoService.calcularVendaIndividualPorId(id);

        assertNotNull(response);
        assertEquals("SUCESSO", response.status());
        assertEquals(id, response.idVenda());
        assertEquals("MATRIC-1", response.matricula());
        assertEquals(new BigDecimal("1000.00"), response.valorVenda());
        assertEquals(new BigDecimal("0.1000"), response.taxaAplicada());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());
        assertEquals("BASE_COMISS", response.origemTaxa());

        verify(resultadoCalculoRepository, times(1)).save(any(ResultadoCalculo.class));
        verify(logCalculoRepository, times(1)).save(any(LogCalculoImutavel.class));
    }

    @Test
    @DisplayName("7. Deve retornar resultado pré-existente sem novo log para reenvio de cálculo individual (Idempotência)")
    void deveRetornarCalculoIndividualExistenteEmReenvio() {
        UUID id = UUID.randomUUID();
        Sale sale = new Sale();
        sale.setId(id);
        sale.setValue(new BigDecimal("1000.00"));

        ResultadoCalculo existente = new ResultadoCalculo(
                UUID.randomUUID(),
                id,
                "MATRIC-1",
                10,
                75,
                100,
                1L,
                LocalDate.of(2026, 9, 15),
                new BigDecimal("1000.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                "INDIVIDUAL"
        );

        when(saleRepository.findById(id)).thenReturn(Optional.of(sale));
        when(resultadoCalculoRepository.findByIdVenda(id)).thenReturn(Optional.of(existente));

        CalculoIndividualResponseDTO response = calculoService.calcularVendaIndividualPorId(id);

        assertEquals("SUCESSO", response.status());
        assertEquals(existente.getProtocoloCalculo(), response.protocoloCalculo());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());

        verify(resultadoCalculoRepository, never()).save(any());
        verify(logCalculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("7.1. Deve tratar concorrência em cálculo individual via DataIntegrityViolationException")
    void deveTratarConcorrenciaEmCalculoIndividual() {
        UUID id = UUID.randomUUID();
        Sale sale = new Sale();
        sale.setId(id);
        sale.setValue(new BigDecimal("1000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));

        Brand brand = new Brand();
        brand.setCode(10);
        sale.setBrand(brand);

        Store store = new Store();
        store.setCode(75);
        sale.setStore(store);

        Position pos = new Position();
        pos.setCode(100);

        Registration reg = new Registration();
        reg.setRegistration("MATRIC-1");
        reg.setPosition(pos);
        sale.setRegistration(reg);

        ResultadoCalculo calculoConcorrente = new ResultadoCalculo(
                UUID.randomUUID(),
                id,
                "MATRIC-1",
                10,
                75,
                100,
                1L,
                LocalDate.of(2026, 9, 15),
                new BigDecimal("1000.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                "INDIVIDUAL"
        );

        when(saleRepository.findById(id)).thenReturn(Optional.of(sale));
        when(resultadoCalculoRepository.findByIdVenda(id))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(calculoConcorrente));

        when(taxaComissaoResolver.resolverTaxa(sale))
                .thenReturn(ResolucaoTaxaResult.sucesso(new BigDecimal("0.1000"), 1L, "BASE_COMISS"));

        when(resultadoCalculoRepository.save(any(ResultadoCalculo.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key error"));

        CalculoIndividualResponseDTO response = calculoService.calcularVendaIndividualPorId(id);

        assertNotNull(response);
        assertEquals("SUCESSO", response.status());
        assertEquals(calculoConcorrente.getProtocoloCalculo(), response.protocoloCalculo());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());

        verify(logCalculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. Deve retornar status IMPEDIDO com motivo contratual quando taxa ou vínculo não forem localizados")
    void deveRetornarStatusImpedidoQuandoHouverImpedimento() {
        UUID id = UUID.randomUUID();
        Sale sale = new Sale();
        sale.setId(id);
        sale.setValue(new BigDecimal("1500.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));

        Registration reg = new Registration();
        reg.setRegistration("MATRIC-999");
        sale.setRegistration(reg);

        when(saleRepository.findById(id)).thenReturn(Optional.of(sale));
        when(resultadoCalculoRepository.findByIdVenda(id)).thenReturn(Optional.empty());
        when(taxaComissaoResolver.resolverTaxa(sale))
                .thenReturn(ResolucaoTaxaResult.impedido("Taxa de comissão não encontrada em tb_basecomiss nem regra ativa"));

        CalculoIndividualResponseDTO response = calculoService.calcularVendaIndividualPorId(id);

        assertNotNull(response);
        assertEquals("IMPEDIDO", response.status());
        assertNull(response.valorComissao());
        assertTrue(response.motivoImpedimento().contains("Taxa de comissão não encontrada"));

        verify(resultadoCalculoRepository, never()).save(any());
        verify(logCalculoRepository, never()).save(any());
    }

    // ==========================================
    // Testes de Cálculo por Competência
    // ==========================================
    @Test
    @DisplayName("9. Deve processar competência calculando vendas válidas e consolidando impedimentos")
    void deveProcessarCompetenciaComSucessosEImpedimentos() {
        LocalDate d1 = LocalDate.of(2026, 9, 10);
        LocalDate d2 = LocalDate.of(2026, 9, 20);

        Sale sale1 = new Sale();
        sale1.setId(UUID.randomUUID());
        sale1.setValue(new BigDecimal("1000.00"));
        sale1.setSaleDate(d1);
        Registration reg1 = new Registration();
        reg1.setRegistration("MATRIC-1");
        Position pos1 = new Position();
        pos1.setCode(100);
        reg1.setPosition(pos1);
        sale1.setRegistration(reg1);
        Brand b1 = new Brand();
        b1.setCode(10);
        sale1.setBrand(b1);

        Sale sale2 = new Sale();
        sale2.setId(UUID.randomUUID());
        sale2.setValue(new BigDecimal("500.00"));
        sale2.setSaleDate(d2);
        Registration reg2 = new Registration();
        reg2.setRegistration("MATRIC-2");
        sale2.setRegistration(reg2);
        sale2.setBrand(b1);

        when(saleRepository.findBySaleDateBetweenOrderBySaleDateAsc(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        )).thenReturn(List.of(sale1, sale2));

        when(taxaComissaoResolver.resolverTaxa(sale1))
                .thenReturn(ResolucaoTaxaResult.sucesso(new BigDecimal("0.1000"), 1L, "BASE_COMISS"));
        when(taxaComissaoResolver.resolverTaxa(sale2))
                .thenReturn(ResolucaoTaxaResult.impedido("Colaborador matrícula 'MATRIC-2' sem cargo vinculado"));

        when(resultadoCalculoRepository.findByIdVenda(sale1.getId())).thenReturn(Optional.empty());
        when(resultadoCalculoRepository.save(any(ResultadoCalculo.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CalculoCompetenciaResponseDTO response = calculoService.calcularPorCompetencia("2026-09");

        assertNotNull(response);
        assertEquals("2026-09", response.competencia());
        assertEquals(2, response.totalVendasProcessadas());
        assertEquals(1, response.totalCalculados());
        assertEquals(1, response.totalImpedimentos());
        assertEquals(new BigDecimal("1000.00"), response.valorTotalVendas());
        assertEquals(new BigDecimal("100.00"), response.valorTotalComissao());

        assertEquals(1, response.resultados().size());
        assertEquals(new BigDecimal("100.00"), response.resultados().get(0).valorComissao());
        assertEquals(1, response.impedimentos().size());
        assertTrue(response.impedimentos().get(0).motivo().contains("sem cargo vinculado"));
    }

    @Test
    @DisplayName("10. Deve rejeitar competência em formato inválido com BusinessException")
    void deveRejeitarCompetenciaInvalida() {
        assertThrows(BusinessException.class, () -> calculoService.calcularPorCompetencia("invalido"));
    }

    // ==========================================
    // Testes de Auditoria e Logs (feat/logs)
    // ==========================================
    @Test
    @DisplayName("11. Deve retornar lista vazia legítima sem dados mock quando não houver registros")
    void deveRetornarListaVaziaSemMock() {
        when(logCalculoRepository.findAllByOrderByExecutadoEmDesc()).thenReturn(List.of());

        List<LogCalculoResponse> logs = calculoService.listarLogs();

        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }

    @Test
    @DisplayName("12. Deve listar logs paginados aplicando especificação e parâmetros de paginação")
    @SuppressWarnings("unchecked")
    void deveListarLogsPaginadosComSucesso() {
        LogCalculoImutavel log = new LogCalculoImutavel(
                UUID.randomUUID(),
                ID_VENDA,
                MATRICULA,
                150,
                75,
                10,
                VALOR_VENDA,
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                1L,
                DATA_VENDA,
                "ECOMMERCE",
                "MOTOR_PRODUCAO"
        );

        Pageable pageable = PageRequest.of(0, 10);
        Page<LogCalculoImutavel> pagedResult = new PageImpl<>(List.of(log), pageable, 1);

        when(logCalculoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(pagedResult);

        Page<LogCalculoResponse> resultado = calculoService.listarLogs(
                ID_VENDA, MATRICULA, 1L, DATA_VENDA.minusDays(5), DATA_VENDA.plusDays(5), pageable
        );

        assertNotNull(resultado);
        assertEquals(1, resultado.getTotalElements());
        assertEquals(1, resultado.getContent().size());
        LogCalculoResponse response = resultado.getContent().get(0);
        assertEquals(MATRICULA, response.matricula());
        assertEquals(ID_VENDA, response.idVenda());
        assertEquals(1L, response.idRegra());
        assertEquals(10, response.codMarca());
        assertEquals(75, response.codLoja());
        assertEquals(150, response.codCargo());
        assertEquals(VALOR_VENDA, response.valorVenda());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());
    }

    @Test
    @DisplayName("13. Deve rejeitar período inválido com BusinessException quando dataInicio for posterior a dataFim")
    void deveRejeitarPeriodoInvalido() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate inicio = LocalDate.of(2026, 12, 31);
        LocalDate fim = LocalDate.of(2026, 12, 1);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                calculoService.listarLogs(null, null, null, inicio, fim, pageable)
        );

        assertTrue(ex.getMessage().contains("Data de início não pode ser posterior"));
    }

    @Test
    @DisplayName("14. Deve buscar log por identificador existente retornando todos os dados preservados")
    void deveBuscarLogPorIdExistente() {
        UUID idLog = UUID.randomUUID();
        LogCalculoImutavel log = new LogCalculoImutavel(
                UUID.randomUUID(),
                ID_VENDA,
                MATRICULA,
                150,
                75,
                10,
                VALOR_VENDA,
                new BigDecimal("0.1000"),
                new BigDecimal("100.00"),
                1L,
                DATA_VENDA,
                "ECOMMERCE",
                "MOTOR_PRODUCAO"
        );

        when(logCalculoRepository.findById(idLog)).thenReturn(Optional.of(log));

        LogCalculoResponse response = calculoService.buscarPorId(idLog);

        assertNotNull(response);
        assertEquals(log.getId(), response.idLog());
        assertEquals(log.getProtocolo(), response.protocolo());
        assertEquals(ID_VENDA, response.idVenda());
        assertEquals(MATRICULA, response.matricula());
        assertEquals(150, response.codCargo());
        assertEquals(75, response.codLoja());
        assertEquals(10, response.codMarca());
        assertEquals(VALOR_VENDA, response.valorVenda());
        assertEquals(new BigDecimal("0.1000"), response.taxaAplicada());
        assertEquals(new BigDecimal("100.00"), response.valorComissao());
        assertEquals(1L, response.idRegra());
        assertEquals(DATA_VENDA, response.dataVenda());
        assertEquals("ECOMMERCE", response.canal());
        assertEquals("MOTOR_PRODUCAO", response.origemExecucao());
        assertEquals("SISTEMA", response.usuarioExecutor());
    }

    @Test
    @DisplayName("15. Deve lançar ResourceNotFoundException quando buscar log com identificador inexistente")
    void deveLancarResourceNotFoundQuandoBuscarIdInexistente() {
        UUID idInexistente = UUID.randomUUID();
        when(logCalculoRepository.findById(idInexistente)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                calculoService.buscarPorId(idInexistente)
        );

        assertTrue(ex.getMessage().contains("Log de cálculo não encontrado"));
        assertTrue(ex.getMessage().contains(idInexistente.toString()));
    }
}

package com.bugbusters.backend.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.interpretador.proposta.AtualizarArtefatosRequest;
import com.bugbusters.backend.dto.interpretador.proposta.FaixaValorDTO;
import com.bugbusters.backend.dto.interpretador.proposta.FiltrosRegraDTO;
import com.bugbusters.backend.dto.interpretador.proposta.OperacaoBaseDTO;
import com.bugbusters.backend.dto.interpretador.proposta.PeriodoVigenciaDTO;
import com.bugbusters.backend.dto.interpretador.proposta.PropostaRegraDTO;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.interpretador.proposta.ValidarPropostaRequest;
import com.bugbusters.backend.dto.interpretador.proposta.ValidarPropostaResponse;
import com.bugbusters.backend.exception.AusenciaCoberturaException;
import com.bugbusters.backend.importbase.ImportResponse;
import com.bugbusters.backend.importbase.ImportService;
import com.bugbusters.backend.importbase.ImportStatus;
import com.bugbusters.backend.importbase.ImportType;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

@SpringBootTest
@ActiveProfiles("test")
public class CoberturaBaseCalculoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CalculoService calculoService;

    @Autowired
    private CoberturaBaseService coberturaBaseService;

    @Autowired
    private InterpretadorService interpretadorService;

    @Autowired
    private ImportService importService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private LogCalculoRepository logCalculoRepository;

    private MockMvc mockMvc;

    private Brand brand10;
    private Position pos150;
    private Store store62;
    private Registration reg123;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        logCalculoRepository.deleteAllInBatch();
        resultadoCalculoRepository.deleteAllInBatch();
        saleRepository.deleteAllInBatch();
        baseComissRepository.deleteAllInBatch();

        brand10 = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });

        pos150 = positionRepository.findByCode(150).orElseGet(() -> {
            Position p = new Position();
            p.setCode(150);
            p.setDescription("VENDEDOR");
            return positionRepository.save(p);
        });

        store62 = storeRepository.findByCode(62).orElseGet(() -> {
            Store s = new Store();
            s.setCode(62);
            s.setDescription("LOJA 62");
            return storeRepository.save(s);
        });

        reg123 = registrationRepository.findByRegistration("MAT-COBERTURA-1").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MAT-COBERTURA-1");
            r.setStore(store62);
            r.setPosition(pos150);
            r.setAdmissDate(LocalDate.of(2025, 1, 1));
            return registrationRepository.save(r);
        });
    }

    // =========================================================================
    // 1. Upload de COMISS sem competência ou com formato inválido -> 400
    // =========================================================================
    @Test
    @DisplayName("Upload de COMISS sem competência deve responder 400 e não iniciar job")
    void uploadComissSemCompetenciaDeveResponder400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "comiss.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes(new Object[][] {{10, "PRETO", 150, "VENDEDOR", 0.05}})
        );

        mockMvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("importType", "COMISSIONS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("competencia")));
    }

    @Test
    @DisplayName("Upload de COMISS com formato inválido de competência deve responder 400")
    void uploadComissFormatoInvalidoDeveResponder400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "comiss.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes(new Object[][] {{10, "PRETO", 150, "VENDEDOR", 0.05}})
        );

        mockMvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("importType", "COMISSIONS")
                        .param("competencia", "2026/10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("YYYY-MM")));
    }

    // =========================================================================
    // 2. Reimportação da mesma base/competência -> rejeitada
    // =========================================================================
    @Test
    @DisplayName("Reimportação de mesma competência e par marca/cargo deve falhar e desfaz alterações")
    void reimportacaoMesmaBaseECompetenciaDeveSerRejeitada() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "comiss.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes(new Object[][] {{10, "PRETO", 150, "VENDEDOR", 0.05}})
        );

        ImportResponse resp1 = awaitReport(importService.processImport(file1, ImportType.COMISSIONS, "2026-10").reportId());
        assertEquals(ImportStatus.CONCLUIDO, resp1.status());

        // Segunda importação com a mesma competência para o mesmo par
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "comiss_dup.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes(new Object[][] {{10, "PRETO", 150, "VENDEDOR", 0.06}})
        );

        ImportResponse resp2 = awaitReport(importService.processImport(file2, ImportType.COMISSIONS, "2026-10").reportId());
        assertEquals(ImportStatus.FALHOU, resp2.status());
        assertTrue(resp2.mensagem().contains("Já existe taxa base") || resp2.mensagem().contains("cadastrada") || resp2.mensagem().contains("duplicada"));
    }

    // =========================================================================
    // 3. Base apenas em mês futuro -> cálculo bloqueado
    // =========================================================================
    @Test
    @DisplayName("Base existente apenas em mês futuro bloqueia cálculo da competência atual")
    void baseApenasEmMesFuturoBloqueiaCalculo() {
        // Cadastra base apenas em 2026-11
        BaseComiss baseFutura = new BaseComiss(brand10, pos150, new BigDecimal("0.0500"), LocalDate.of(2026, 11, 1));
        baseComissRepository.save(baseFutura);

        // Venda ocorre em 2026-10
        Sale sale = new Sale();
        sale.setBrand(brand10);
        sale.setRegistration(reg123);
        sale.setStore(store62);
        sale.setValue(new BigDecimal("1000.00"));
        sale.setSaleDate(LocalDate.of(2026, 10, 15));
        Sale saleSalva = saleRepository.save(sale);

        // Cálculo por competência 2026-10 deve ser bloqueado
        AusenciaCoberturaException exComp = assertThrows(AusenciaCoberturaException.class, () ->
                calculoService.calcularPorCompetencia("2026-10")
        );
        assertEquals(YearMonth.of(2026, 10), exComp.getCompetencia());
        assertFalse(exComp.getReferenciasFaltantes().isEmpty());

        // Cálculo individual por venda ID deve ser bloqueado
        AusenciaCoberturaException exInd = assertThrows(AusenciaCoberturaException.class, () ->
                calculoService.calcularVendaIndividualPorId(saleSalva.getId())
        );
        assertEquals(YearMonth.of(2026, 10), exInd.getCompetencia());

        // Cálculo avulso deve ser bloqueado
        CalculoComissaoRequest reqAvulso = new CalculoComissaoRequest(
                UUID.randomUUID(), reg123.getRegistration(), new BigDecimal("1000.00"),
                LocalDate.of(2026, 10, 15), 10, "PRETO", 62, "LOJA"
        );
        assertThrows(AusenciaCoberturaException.class, () ->
                calculoService.calcularComissao(reqAvulso)
        );
    }

    // =========================================================================
    // 4. Cálculo individual e avulso bloqueados sem base e zero escritas
    // =========================================================================
    @Test
    @DisplayName("Cálculo individual e avulso bloqueados sem base garantindo zero escritas no banco e log")
    void calculoBloqueadoSemBaseGaranteZeroEscritas() {
        // tb_basecomiss vazia
        assertEquals(0, baseComissRepository.count());

        Sale sale = new Sale();
        sale.setBrand(brand10);
        sale.setRegistration(reg123);
        sale.setStore(store62);
        sale.setValue(new BigDecimal("2500.00"));
        sale.setSaleDate(LocalDate.of(2026, 10, 10));
        Sale saleSalva = saleRepository.save(sale);

        long resAntes = resultadoCalculoRepository.count();
        long logAntes = logCalculoRepository.count();

        // 1. Individual
        assertThrows(AusenciaCoberturaException.class, () ->
                calculoService.calcularVendaIndividualPorId(saleSalva.getId())
        );
        assertEquals(resAntes, resultadoCalculoRepository.count(), "Zero registros escritos em tb_resultado_calculo");
        assertEquals(logAntes, logCalculoRepository.count(), "Zero registros escritos em tb_log_calculo_imutavel");

        // 2. Avulso
        CalculoComissaoRequest reqAvulso = new CalculoComissaoRequest(
                UUID.randomUUID(), reg123.getRegistration(), new BigDecimal("1500.00"),
                LocalDate.of(2026, 10, 10), 10, "PRETO", 62, "ECOMMERCE"
        );
        assertThrows(AusenciaCoberturaException.class, () ->
                calculoService.calcularComissao(reqAvulso)
        );
        assertEquals(resAntes, resultadoCalculoRepository.count(), "Zero registros escritos em tb_resultado_calculo");
        assertEquals(logAntes, logCalculoRepository.count(), "Zero registros escritos em tb_log_calculo_imutavel");

        // 3. Competência
        assertThrows(AusenciaCoberturaException.class, () ->
                calculoService.calcularPorCompetencia("2026-10")
        );
        assertEquals(resAntes, resultadoCalculoRepository.count(), "Zero registros escritos em tb_resultado_calculo");
        assertEquals(logAntes, logCalculoRepository.count(), "Zero registros escritos em tb_log_calculo_imutavel");
    }

    // =========================================================================
    // 5. Venda sem matrícula, sem cargo ou sem marca entra na lista de faltantes
    // =========================================================================
    @Test
    @DisplayName("Venda sem matrícula ou sem cargo entra na lista de faltantes com null")
    void vendaSemMatriculaOuCargoEntraComoFaltante() {
        Sale vendaSemReg = new Sale();
        vendaSemReg.setBrand(brand10);
        vendaSemReg.setRegistration(null);
        vendaSemReg.setStore(store62);
        vendaSemReg.setValue(new BigDecimal("500.00"));
        vendaSemReg.setSaleDate(LocalDate.of(2026, 10, 5));

        AusenciaCoberturaException ex = assertThrows(AusenciaCoberturaException.class, () ->
                coberturaBaseService.validarCobertura(vendaSemReg)
        );

        assertNotNull(ex.getReferenciasFaltantes());
        assertEquals(1, ex.getReferenciasFaltantes().size());
        var faltante = ex.getReferenciasFaltantes().get(0);
        assertEquals(10, faltante.codMarca());
        assertEquals(null, faltante.codCargo());
    }

    // =========================================================================
    // 6. tb_basecomiss vazia gerando pendência impeditiva na IA
    // =========================================================================
    @Test
    @DisplayName("tb_basecomiss vazia gera pendência impeditiva BASE_REFERENCIA_NAO_ENCONTRADA na IA")
    void baseVaziaGeraPendenciaImpeditivaNaIa() {
        assertEquals(0, baseComissRepository.count());

        PropostaRegraDTO proposta = new PropostaRegraDTO(
                "bloco-1",
                "Regra Sem Base",
                new FiltrosRegraDTO("ECOMMERCE", 10, "PRETO", null, 150, "VENDEDOR", null),
                FaixaValorDTO.semLimites(),
                new OperacaoBaseDTO(TipoOperacaoBase.ACRESCIMO_PONTOS, new BigDecimal("0.0100"), null),
                new BigDecimal("0.0600"),
                null,
                null,
                List.of(),
                false,
                null,
                null,
                new PeriodoVigenciaDTO(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
                null,
                null
        );

        // 1. validarProposta
        ValidarPropostaResponse validacao = interpretadorService.validarProposta(new ValidarPropostaRequest(
                null, 10, "PRETO", 150, "VENDEDOR", null, "ECOMMERCE", null, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)
        ));
        assertFalse(validacao.valida(), "Proposta não deve ser válida sem taxa base quando a taxa é nula (operação sobre base)");
        assertTrue(validacao.erros().stream().anyMatch(e -> e.contains("tb_basecomiss")));

        // 2. atualizarArtefatos
        var artefato = interpretadorService.atualizarArtefatos(new AtualizarArtefatosRequest(proposta));
        assertFalse(artefato.completa(), "Artefatos devem estar incompletos sem taxa base");
        assertTrue(artefato.pendencias().stream().anyMatch(p ->
                "BASE_REFERENCIA_NAO_ENCONTRADA".equals(p.codigo()) && p.isImpeditiva()
        ));
    }

    private static byte[] excelBytes(Object[][] values) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Cabeçalho");
            for (int i = 0; i < values.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int j = 0; j < values[i].length; j++) {
                    Object value = values[i][j];
                    if (value instanceof Number number) {
                        row.createCell(j).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(j).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private ImportResponse awaitReport(long id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        do {
            ImportResponse report = importService.getReport(id);
            if (report.status() == ImportStatus.CONCLUIDO || report.status() == ImportStatus.FALHOU) {
                return report;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Import did not finish within 15 seconds: " + id);
    }
}

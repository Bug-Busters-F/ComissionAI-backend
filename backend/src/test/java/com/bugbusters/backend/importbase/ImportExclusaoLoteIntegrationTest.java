package com.bugbusters.backend.importbase;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.importbase.dto.ExclusaoLoteResponseDTO;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ImportExclusaoLoteIntegrationTest {

    @Autowired ImportService importService;
    @Autowired EnvioArquivoRepository envioArquivoRepository;
    @Autowired ResultadoCalculoRepository resultadoCalculoRepository;
    @Autowired LogCalculoRepository logCalculoRepository;
    @Autowired RegraRepository regraRepository;
    @Autowired JobRepository jobRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRollbackRunner rollbackRunner;

    private String testMatricula;

    @BeforeEach
    void setUp() {
        testMatricula = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void rollbackRealFalhaForcadaAposPrimeiroDeleteMantemDadosEStatusConcluido() throws Exception {
        String reg = UUID.randomUUID().toString();
        complete(excel(new Object[][] { hr(reg) }), ImportType.HR, null);

        ImportResponse salesReport = complete(excel(new Object[][] {
                {"2026-10-01", 991, "Marca", 991, "Loja", reg, 100.0},
                {"2026-10-02", 991, "Marca", 991, "Loja", reg, 200.0}
        }), ImportType.SALES, "2026-10");
        assertEquals(ImportStatus.CONCLUIDO, salesReport.status());

        EnvioArquivo lote = envioArquivoRepository.findByReportId(salesReport.reportId()).orElseThrow();
        Long countAntes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_sales WHERE id_lote_origem = ?", Long.class, lote.getIdLote());
        assertEquals(2L, countAntes);

        assertThrows(RuntimeException.class, () ->
                rollbackRunner.executarExclusaoComFalha(salesReport.reportId()));

        // Confirma que os dados foram totalmente preservados no banco e o lote continua CONCLUIDO
        Long countDepois = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_sales WHERE id_lote_origem = ?", Long.class, lote.getIdLote());
        assertEquals(2L, countDepois, "Vendas não podem ser removidas se houver rollback na transação");

        EnvioArquivo loteDepois = envioArquivoRepository.findByReportId(salesReport.reportId()).orElseThrow();
        assertEquals(ImportStatus.CONCLUIDO.name(), loteDepois.getStatus(), "Status deve permanecer CONCLUIDO");
    }

    @Test
    void reconciliacaoLoteProcessandoComJobFailedPermiteExclusaoLogica() {
        String idLote = UUID.randomUUID().toString();
        JobParameters params = new JobParametersBuilder()
                .addString("requestId", idLote)
                .addString("importType", "SALES")
                .toJobParameters();

        JobInstance instance = jobRepository.createJobInstance("importJob", params);
        JobExecution execution = jobRepository.createJobExecution(instance, params, new org.springframework.batch.infrastructure.item.ExecutionContext());
        execution.setStatus(BatchStatus.FAILED);
        jobRepository.update(execution);

        EnvioArquivo lote = new EnvioArquivo();
        lote.setIdLote(idLote);
        lote.setReportId(execution.getId());
        lote.setNomeArquivo("falho.xlsx");
        lote.setTipoBase("SALES");
        lote.setCompetencia("2026-10");
        lote.setStatus(ImportStatus.PROCESSANDO.name());
        lote.setCriadoEm(OffsetDateTime.now());
        envioArquivoRepository.saveAndFlush(lote);

        ExclusaoLoteResponseDTO res = importService.excluirLote(execution.getId(), "admin", "Exclusão de falho");
        assertNotNull(res);
        assertEquals(ImportStatus.EXCLUIDO.name(), res.status());
        assertEquals(0L, res.registrosRemovidos());

        EnvioArquivo atualizado = envioArquivoRepository.findByReportId(execution.getId()).orElseThrow();
        assertEquals(ImportStatus.EXCLUIDO.name(), atualizado.getStatus());
    }

    @Test
    void testeEmAndamentoGravandoEstadoDiretoSemWorkerBloqueado() {
        String idLote = UUID.randomUUID().toString();
        JobParameters params = new JobParametersBuilder()
                .addString("requestId", idLote)
                .addString("importType", "SALES")
                .toJobParameters();

        JobInstance instance = jobRepository.createJobInstance("importJob", params);
        JobExecution execution = jobRepository.createJobExecution(instance, params, new org.springframework.batch.infrastructure.item.ExecutionContext());
        execution.setStatus(BatchStatus.STARTED);
        jobRepository.update(execution);

        EnvioArquivo lote = new EnvioArquivo();
        lote.setIdLote(idLote);
        lote.setReportId(execution.getId());
        lote.setNomeArquivo("em_andamento.xlsx");
        lote.setTipoBase("SALES");
        lote.setCompetencia("2026-10");
        lote.setStatus(ImportStatus.PROCESSANDO.name());
        lote.setCriadoEm(OffsetDateTime.now());
        envioArquivoRepository.saveAndFlush(lote);

        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
                importService.excluirLote(execution.getId(), "admin", "cancelar"));
        assertTrue(ex.getMessage().contains("em andamento"));
    }

    @Test
    void hrComMatriculaUsadaEmRegraDeCampanhaResultaEm409() throws Exception {
        ImportResponse hrReport = complete(excel(new Object[][] { hr(testMatricula) }), ImportType.HR, null);
        assertEquals(ImportStatus.CONCLUIDO, hrReport.status());

        Regra regra = new Regra();
        regra.setNome("Regra Teste HR");
        regra.setMatricula(testMatricula);
        regra.setTaxa(new BigDecimal("0.0500"));
        regra.setDataInicio(LocalDate.of(2026, 1, 1));
        regra.setDataFim(LocalDate.of(2026, 12, 31));
        regra.setStatus(StatusRegra.ATIVA);
        regraRepository.saveAndFlush(regra);

        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
                importService.excluirLote(hrReport.reportId(), "admin", "tentativa"));

        assertTrue(ex.getValidacoes().stream().anyMatch(v -> "regras_campanha".equals(v.campo())));
    }

    @Test
    void commissionsDeSetembroComCalculoSoEmAgostoPermiteExclusao() throws Exception {
        int brandCode = 881;
        int positionCode = 881;
        ImportResponse comReport = complete(excel(new Object[][] {
                {brandCode, "Marca 881", positionCode, "Cargo 881", 0.04}
        }), ImportType.COMISSIONS, "2026-09");
        assertEquals(ImportStatus.CONCLUIDO, comReport.status());

        ResultadoCalculo resultadoAgosto = new ResultadoCalculo(
                UUID.randomUUID(),
                UUID.randomUUID(),
                testMatricula,
                brandCode,
                100,
                positionCode,
                1L,
                LocalDate.of(2026, 8, 15),
                new BigDecimal("100.00"),
                new BigDecimal("0.0400"),
                new BigDecimal("4.00"),
                "INDIVIDUAL"
        );
        resultadoCalculoRepository.saveAndFlush(resultadoAgosto);

        ExclusaoLoteResponseDTO res = importService.excluirLote(comReport.reportId(), "admin", "ok");
        assertEquals(ImportStatus.EXCLUIDO.name(), res.status());
        assertTrue(res.registrosRemovidos() >= 1);
    }

    @Test
    void excluirUmLoteSalesNaoAfetaOutroLoteSales() throws Exception {
        String reg = UUID.randomUUID().toString();
        complete(excel(new Object[][] { hr(reg) }), ImportType.HR, null);

        ImportResponse sales1 = complete(excel(new Object[][] {
                {"2026-10-01", 991, "Marca", 991, "Loja", reg, 100.0},
                {"2026-10-02", 991, "Marca", 991, "Loja", reg, 200.0}
        }), ImportType.SALES, "2026-10");

        ImportResponse sales2 = complete(excel(new Object[][] {
                {"2026-10-03", 991, "Marca", 991, "Loja", reg, 300.0},
                {"2026-10-04", 991, "Marca", 991, "Loja", reg, 400.0}
        }), ImportType.SALES, "2026-10");

        assertEquals(ImportStatus.CONCLUIDO, sales1.status());
        assertEquals(ImportStatus.CONCLUIDO, sales2.status());

        ExclusaoLoteResponseDTO del = importService.excluirLote(sales1.reportId(), "admin", "remover 1");
        assertEquals(2L, del.registrosRemovidos());

        EnvioArquivo lote2 = envioArquivoRepository.findByReportId(sales2.reportId()).orElseThrow();
        Long vendasRestantesLote2 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_sales WHERE id_lote_origem = ?", Long.class, lote2.getIdLote());
        assertEquals(2L, vendasRestantesLote2);
    }

    @Test
    void quatrocentosENoveListaMultiplosMotivos() throws Exception {
        ImportResponse hrReport = complete(excel(new Object[][] { hr(testMatricula) }), ImportType.HR, null);
        assertEquals(ImportStatus.CONCLUIDO, hrReport.status());

        // 1. Venda vinculada
        complete(excel(new Object[][] {
                {"2026-10-01", 991, "Marca", 991, "Loja", testMatricula, 100.0}
        }), ImportType.SALES, "2026-10");

        // 2. Regra de campanha vinculada
        Regra regra = new Regra();
        regra.setNome("Regra Multiplos");
        regra.setMatricula(testMatricula);
        regra.setTaxa(new BigDecimal("0.0500"));
        regra.setDataInicio(LocalDate.of(2026, 1, 1));
        regra.setDataFim(LocalDate.of(2026, 12, 31));
        regra.setStatus(StatusRegra.ATIVA);
        regraRepository.saveAndFlush(regra);

        // 3. Resultado de cálculo vinculado
        ResultadoCalculo resultado = new ResultadoCalculo(
                UUID.randomUUID(),
                UUID.randomUUID(),
                testMatricula,
                991,
                991,
                991,
                regra.getId(),
                LocalDate.of(2026, 10, 1),
                new BigDecimal("100.00"),
                new BigDecimal("0.0500"),
                new BigDecimal("5.00"),
                "INDIVIDUAL"
        );
        resultadoCalculoRepository.saveAndFlush(resultado);

        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
                importService.excluirLote(hrReport.reportId(), "admin", "testar multiplos"));

        List<CampoInvalidoDTO> validacoes = ex.getValidacoes();
        assertTrue(validacoes.size() >= 2, "Deve retornar múltiplos motivos de bloqueio");
        assertTrue(validacoes.stream().anyMatch(v -> "vendas".equals(v.campo())));
        assertTrue(validacoes.stream().anyMatch(v -> "regras_campanha".equals(v.campo())));
        assertTrue(validacoes.stream().anyMatch(v -> "calculos".equals(v.campo())));
    }

    private ImportResponse complete(MockMultipartFile file, ImportType type, String competencia) throws Exception {
        long id = importService.processImport(file, type, competencia).reportId();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        do {
            ImportResponse report = importService.getReport(id);
            if (report.status() == ImportStatus.CONCLUIDO || report.status() == ImportStatus.FALHOU) {
                return report;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Import did not finish within 15 seconds: " + id);
    }

    private static Object[] hr(String registration) {
        return new Object[] {"2026-10-01", 991, "Marca Batch", 991, "Loja Batch", registration,
                "2026-01-01", null, 991, "Cargo Batch"};
    }

    private static MockMultipartFile excel(Object[][] values) throws Exception {
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
            return new MockMultipartFile("file", "base.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestRollbackRunner testRollbackRunner(ImportService importService) {
            return new TestRollbackRunner(importService);
        }
    }

    public static class TestRollbackRunner {
        private final ImportService importService;

        public TestRollbackRunner(ImportService importService) {
            this.importService = importService;
        }

        @Transactional
        public void executarExclusaoComFalha(long reportId) {
            importService.excluirLote(reportId, "admin", "teste rollback");
            throw new RuntimeException("Falha forçada após primeiro DELETE para testar rollback");
        }
    }
}

package com.bugbusters.backend.importbase;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import com.bugbusters.backend.exception.ResourceNotFoundException;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import com.bugbusters.backend.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ImportBatchIntegrationTest {
    @Autowired ImportService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("importTaskExecutor") ThreadPoolTaskExecutor executor;

    @Test
    void importsAllTypesAndPersistsBatchMetadata() throws Exception {
        String registration = UUID.randomUUID().toString();
        assertEquals(1L, complete(excel(new Object[][] {hr(registration)}), ImportType.HR).totalLinhas());
        assertEquals(1L, complete(excel(new Object[][] {
                {"2026-10-01", 991, "Marca Batch", 991, "Loja Batch", registration, 150.0}
        }), ImportType.SALES, "2026-10").totalLinhas());
        assertEquals(1L, complete(excel(new Object[][] {
                {991, "Marca Batch", 991, "Cargo Batch", 0.05}
        }), ImportType.COMISSIONS, "2026-10").totalLinhas());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE STATUS = 'COMPLETED'", Long.class) >= 3);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM tb_sales WHERE id_lote_origem IS NOT NULL", Long.class) >= 1);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM tb_registration WHERE id_lote_origem IS NOT NULL", Long.class) >= 1);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM tb_basecomiss WHERE id_lote_origem IS NOT NULL", Long.class) >= 1);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM tb_envio_arquivo WHERE status = 'CONCLUIDO'", Long.class) >= 3);
        for (String filePath : jdbc.queryForList("SELECT PARAMETER_VALUE FROM BATCH_JOB_EXECUTION_PARAMS WHERE PARAMETER_NAME = 'filePath'", String.class)) {
            assertFalse(Files.exists(Path.of(filePath)), "Temporary upload must be removed after execution");
        }
    }

    @Test
    void lateInvalidRowRollsBackEvenAfterFlushAndClear() throws Exception {
        long registrations = count("tb_registration");
        long stores = count("tb_store");
        long positions = count("tb_position");
        Object[][] rows = new Object[102][];
        for (int i = 0; i < 101; i++) {
            rows[i] = hr(UUID.randomUUID().toString());
            rows[i][3] = 992;
            rows[i][8] = 992;
        }
        rows[101] = hr("");
        ImportResponse report = complete(excel(rows), ImportType.HR);
        assertEquals(ImportStatus.FALHOU, report.status());
        assertEquals(0L, report.totalLinhas());
        assertTrue(report.mensagem().contains("Linha 103"));
        assertEquals(registrations, count("tb_registration"));
        assertEquals(stores, count("tb_store"));
        assertEquals(positions, count("tb_position"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE STATUS = 'FAILED'", Long.class) >= 1);
    }

    @Test
    void duplicateRegistrationRollsBackAuxiliaryRecords() throws Exception {
        long registrations = count("tb_registration");
        long stores = count("tb_store");
        long positions = count("tb_position");
        String registration = UUID.randomUUID().toString();
        Object[] first = hr(registration);
        first[3] = 993;
        first[8] = 993;
        assertEquals(ImportStatus.FALHOU, complete(excel(new Object[][] {first, first}), ImportType.HR).status());
        assertEquals(registrations, count("tb_registration"));
        assertEquals(stores, count("tb_store"));
        assertEquals(positions, count("tb_position"));
    }

    @Test
    void rejectsUnknownRegistrationInsteadOfSilentlyDroppingSale() throws Exception {
        long sales = count("tb_sales");
        ImportResponse report = complete(excel(new Object[][] {
                        {"2026-10-01", 994, "Marca", 994, "Loja", UUID.randomUUID().toString(), 10.0}
                }), ImportType.SALES, "2026-10");
        assertEquals(ImportStatus.FALHOU, report.status());
        assertTrue(report.mensagem().contains("Matrícula não cadastrada"));
        assertEquals(sales, count("tb_sales"));
    }

    @Test
    void rejectsEmptyUploadsAndHeaderOnlyWorkbooks() throws Exception {
        assertThrows(BusinessException.class, () -> service.processImport(
                new MockMultipartFile("file", new byte[0]), ImportType.HR));
        assertEquals(ImportStatus.FALHOU, complete(excel(new Object[0][]), ImportType.HR).status());
        assertEquals(ImportStatus.FALHOU, complete(
                new MockMultipartFile("file", "base.csv", "text/csv", "coluna\nvalor".getBytes()), ImportType.HR).status());
    }

    @Test
    void returnsPendingReportBeforeWorkerProcessesAndKeepsUploadUntilCompletion() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                release.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        ImportResponse accepted;
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            accepted = service.processImport(excel(new Object[][] {hr(UUID.randomUUID().toString())}), ImportType.HR);
            assertTrue(accepted.reportId() > 0);
            assertEquals(ImportStatus.PENDENTE, accepted.status());
            assertNull(accepted.totalLinhas());
            assertEquals(accepted, service.getReport(accepted.reportId()));
            String path = jdbc.queryForObject("SELECT PARAMETER_VALUE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = ? AND PARAMETER_NAME = 'filePath'", String.class, accepted.reportId());
            assertTrue(Files.exists(Path.of(path)));
        } finally {
            release.countDown();
        }
        assertEquals(ImportStatus.CONCLUIDO, awaitReport(accepted.reportId()).status());
    }

    @Test
    void unknownReportIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.getReport(Long.MAX_VALUE));
    }

    @Test
    void fullQueueReturnsFailedReportAndRemovesUpload() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(20);
        executor.execute(() -> {
            started.countDown();
            try {
                release.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) {
                executor.execute(drained::countDown);
            }
            ImportResponse report = service.processImport(excel(new Object[][] {hr(UUID.randomUUID().toString())}), ImportType.HR);
            assertEquals(ImportStatus.FALHOU, report.status());
            assertEquals(0L, report.totalLinhas());
            String path = jdbc.queryForObject("SELECT PARAMETER_VALUE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = ? AND PARAMETER_NAME = 'filePath'", String.class, report.reportId());
            assertFalse(Files.exists(Path.of(path)));
        } finally {
            release.countDown();
            assertTrue(drained.await(5, TimeUnit.SECONDS));
        }
    }

    private ImportResponse complete(MockMultipartFile file, ImportType type) throws Exception {
        return complete(file, type, null);
    }

    private ImportResponse complete(MockMultipartFile file, ImportType type, String competencia) throws Exception {
        return awaitReport(service.processImport(file, type, competencia).reportId());
    }

    private ImportResponse awaitReport(long id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        do {
            ImportResponse report = service.getReport(id);
            if (report.status() == ImportStatus.CONCLUIDO || report.status() == ImportStatus.FALHOU) {
                return report;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Import did not finish within 15 seconds: " + id);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
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
}

package com.bugbusters.backend.importbase;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.repository.EnvioArquivoRepository;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ImportHashBloqueioIntegrationTest {

    @Autowired
    private ImportService service;

    @Autowired
    private EnvioArquivoRepository envioArquivoRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("Reenvio do mesmo arquivo para a mesma competência enquanto lote ativo bloqueia com 409")
    void reenvioMesmoArquivoMesmaCompetenciaComLoteAtivoBloqueiaCom409() throws Exception {
        String reg = UUID.randomUUID().toString();
        // Garante matrícula existente
        complete(excel(new Object[][] { hr(reg) }), ImportType.HR, null);

        MockMultipartFile fileSales = excel(new Object[][] {
                {"2026-10-01", 991, "Marca Hash", 991, "Loja Hash", reg, 150.0}
        });

        ImportResponse resp1 = complete(fileSales, ImportType.SALES, "2026-10");
        assertEquals(ImportStatus.CONCLUIDO, resp1.status());

        // Segundo envio com o exato mesmo arquivo e competência
        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
                service.processImport(fileSales, ImportType.SALES, "2026-10")
        );

        assertTrue(ex.getMessage().contains("Já existe um lote ativo"));
        assertTrue(ex.getMessage().contains("CONCLUIDO"));
    }

    @Test
    @DisplayName("Reenvio do mesmo arquivo após exclusão do lote anterior é permitido com sucesso")
    void reenvioMesmoArquivoAposExclusaoDoLoteAnteriorPermitido() throws Exception {
        String reg = UUID.randomUUID().toString();
        complete(excel(new Object[][] { hr(reg) }), ImportType.HR, null);

        MockMultipartFile fileSales = excel(new Object[][] {
                {"2026-10-02", 991, "Marca Hash", 991, "Loja Hash", reg, 250.0}
        });

        ImportResponse resp1 = complete(fileSales, ImportType.SALES, "2026-10");
        assertEquals(ImportStatus.CONCLUIDO, resp1.status());

        // Exclui o lote anterior
        service.excluirLote(resp1.reportId(), "TEST_USER", "Exclusão para reenvio");

        // Agora o reenvio do mesmo arquivo e mesma competência deve ser aceito
        ImportResponse resp2 = complete(fileSales, ImportType.SALES, "2026-10");
        assertEquals(ImportStatus.CONCLUIDO, resp2.status());
        assertNotEquals(resp1.reportId(), resp2.reportId());
    }

    @Test
    @DisplayName("Mesmo arquivo enviado para competências distintas é permitido")
    void mesmoArquivoParaCompetenciasDiferentesPermitido() throws Exception {
        int brandCode = 910 + (int) (System.currentTimeMillis() % 1000);
        int posCode = 910 + (int) (System.currentTimeMillis() % 1000);

        MockMultipartFile fileComiss = excel(new Object[][] {
                {brandCode, "Marca " + brandCode, posCode, "Cargo " + posCode, 0.07}
        });

        ImportResponse resp1 = complete(fileComiss, ImportType.COMISSIONS, "2026-10");
        assertEquals(ImportStatus.CONCLUIDO, resp1.status());

        // Reenvia o mesmo arquivo com o mesmo hash, mas para competência 2026-11
        ImportResponse resp2 = complete(fileComiss, ImportType.COMISSIONS, "2026-11");
        assertEquals(ImportStatus.CONCLUIDO, resp2.status());
        assertNotEquals(resp1.reportId(), resp2.reportId());
    }

    @Test
    @DisplayName("Arquivos diferentes para a mesma competência são permitidos (lotes complementares)")
    void arquivosDiferentesMesmaCompetenciaPermitidos() throws Exception {
        String reg1 = UUID.randomUUID().toString();
        String reg2 = UUID.randomUUID().toString();
        complete(excel(new Object[][] { hr(reg1), hr(reg2) }), ImportType.HR, null);

        MockMultipartFile file1 = excel(new Object[][] {
                {"2026-10-05", 991, "Marca Hash", 991, "Loja Hash", reg1, 100.0}
        });
        MockMultipartFile file2 = excel(new Object[][] {
                {"2026-10-06", 991, "Marca Hash", 991, "Loja Hash", reg2, 200.0}
        });

        ImportResponse resp1 = complete(file1, ImportType.SALES, "2026-10");
        ImportResponse resp2 = complete(file2, ImportType.SALES, "2026-10");

        assertEquals(ImportStatus.CONCLUIDO, resp1.status());
        assertEquals(ImportStatus.CONCLUIDO, resp2.status());
    }

    @Test
    @DisplayName("Mesmo arquivo HR (competência null) com lote ativo bloqueia com 409")
    void mesmoArquivoHRComLoteAtivoBloqueiaCom409() throws Exception {
        String reg = UUID.randomUUID().toString();
        MockMultipartFile fileHR = excel(new Object[][] { hr(reg) });

        ImportResponse resp1 = complete(fileHR, ImportType.HR, null);
        assertEquals(ImportStatus.CONCLUIDO, resp1.status());

        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
                service.processImport(fileHR, ImportType.HR)
        );

        assertTrue(ex.getMessage().contains("Já existe um lote ativo"));
    }

    @Test
    @DisplayName("Lote candidato em PROCESSANDO com Job FAILED é reconciliado para FALHOU e não bloqueia reenvio")
    void loteCandidatoReconciliaParaFalhouENaoBloqueia() throws Exception {
        String reg = UUID.randomUUID().toString();
        MockMultipartFile file = excel(new Object[][] {
                {"2026-10-10", 991, "Marca Reconcilia", 991, "Loja Reconcilia", reg, 500.0}
        });

        // 1. Simula um lote que ficou travado em PROCESSANDO mas cujo Spring Batch job de fato FALHOU
        String idLoteTravado = UUID.randomUUID().toString();
        JobParameters params = new JobParametersBuilder()
                .addString("requestId", idLoteTravado)
                .addString("importType", "SALES")
                .toJobParameters();

        JobInstance instance = jobRepository.createJobInstance("importJob", params);
        JobExecution execution = jobRepository.createJobExecution(instance, params, new ExecutionContext());
        execution.setStatus(BatchStatus.FAILED);
        jobRepository.update(execution);

        // Calcula hash do arquivo para simular lote com o mesmo hash
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String hash = java.util.HexFormat.of().formatHex(md.digest(file.getBytes()));

        EnvioArquivo loteTravado = new EnvioArquivo();
        loteTravado.setIdLote(idLoteTravado);
        loteTravado.setReportId(execution.getId());
        loteTravado.setNomeArquivo("vendas_reconcilia.xlsx");
        loteTravado.setTipoBase("SALES");
        loteTravado.setCompetencia("2026-10");
        loteTravado.setHashConteudo(hash);
        loteTravado.setStatus("PROCESSANDO");
        loteTravado.setTotalLinhas(0);
        loteTravado.setLinhasValidas(0);
        loteTravado.setRejeicaoIntegral(false);
        loteTravado.setCriadoEm(OffsetDateTime.now());
        envioArquivoRepository.saveAndFlush(loteTravado);

        // Cria a matrícula para permitir que a importação subsequente execute
        complete(excel(new Object[][] { hr(reg) }), ImportType.HR, null);

        // 2. Chamar o envio com o mesmo arquivo: não deve bloquear, deve reconciliar o anterior para FALHOU e iniciar novo
        ImportResponse novoLote = complete(file, ImportType.SALES, "2026-10");
        assertEquals(ImportStatus.CONCLUIDO, novoLote.status());

        // Verifica que o lote anterior agora está FALHOU
        EnvioArquivo loteAnterior = envioArquivoRepository.findByIdLote(idLoteTravado).orElseThrow();
        assertEquals("FALHOU", loteAnterior.getStatus());
    }

    @Test
    @DisplayName("Endpoint POST /api/v1/imports/upload responde 409 Conflict quando arquivo duplicado é reenviado")
    void controllerResponde409ParaArquivoDuplicado() throws Exception {
        String reg = UUID.randomUUID().toString();
        complete(excel(new Object[][] { hr(reg) }), ImportType.HR, null);

        MockMultipartFile file = excel(new Object[][] {
                {"2026-10-12", 991, "Marca Controller", 991, "Loja Controller", reg, 350.0}
        });

        // Primeiro upload: 202 Accepted
        mockMvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("importType", "SALES")
                        .param("competencia", "2026-10"))
                .andExpect(status().isAccepted());

        // Segundo upload do mesmo arquivo: 409 Conflict
        mockMvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("importType", "SALES")
                        .param("competencia", "2026-10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("Já existe um lote ativo")));
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

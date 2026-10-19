package com.bugbusters.backend.importbase.batch;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.persistence.EntityManager;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.importbase.EnvioArquivoTxService;
import com.bugbusters.backend.importbase.ImportInconsistencySeverity;
import com.bugbusters.backend.importbase.ImportType;
import com.bugbusters.backend.importbase.reader.*;
import com.bugbusters.backend.model.InconsistenciaImportacao;
import com.bugbusters.backend.registration.*;
import com.bugbusters.backend.sales.*;
import com.bugbusters.backend.basecomiss.*;

/** One transaction per file, including auxiliary entities created by the mappers. */
@Component
public class ImportTasklet implements Tasklet {
    private final HrFileReader hrReader;
    private final SalesFileReader salesReader;
    private final CommissFileReader commissReader;
    private final RegistrationMapper registrationMapper;
    private final RegistrationRepository registrations;
    private final SaleMapper saleMapper;
    private final SaleRepository sales;
    private final BaseComissMapper commissMapper;
    private final BaseComissRepository commissions;
    private final EntityManager entityManager;
    private final EnvioArquivoTxService txService;

    public ImportTasklet(HrFileReader hrReader, SalesFileReader salesReader, CommissFileReader commissReader,
            RegistrationMapper registrationMapper, RegistrationRepository registrations,
            SaleMapper saleMapper, SaleRepository sales, BaseComissMapper commissMapper,
            BaseComissRepository commissions, EntityManager entityManager,
            EnvioArquivoTxService txService) {
        this.hrReader = hrReader;
        this.salesReader = salesReader;
        this.commissReader = commissReader;
        this.registrationMapper = registrationMapper;
        this.registrations = registrations;
        this.saleMapper = saleMapper;
        this.sales = sales;
        this.commissMapper = commissMapper;
        this.commissions = commissions;
        this.entityManager = entityManager;
        this.txService = txService;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext context) throws Exception {
        var parameters = contribution.getStepExecution().getJobParameters();
        ImportType type = ImportType.valueOf(parameters.getString("importType"));
        String requestId = parameters.getString("requestId");
        var executionContext = contribution.getStepExecution().getJobExecution().getExecutionContext();

        try (InputStream input = Files.newInputStream(Path.of(parameters.getString("filePath")))) {
            switch (type) {
                case HR -> {
                    Set<String> matriculasProcessadasNoArquivo = new HashSet<>();
                    int[] ignoradasExistentes = new int[]{0};

                    hrReader.forEach(input, row -> {
                        required(row.getRegistration(), "matrícula");
                        required(row.getStoreCode(), "código da loja");
                        required(row.getPositionCode(), "código do cargo");
                        required(row.getAdmissDate(), "data de admissão");

                        contribution.incrementReadCount();

                        String matricula = row.getRegistration().trim();
                        if (!matriculasProcessadasNoArquivo.add(matricula) || registrations.existsByRegistration(matricula)) {
                            ignoradasExistentes[0]++;
                            return;
                        }

                        Registration registration = registrationMapper.toEntity(row);
                        registration.setIdLoteOrigem(requestId);
                        registrations.save(registration);
                        recordWrite(contribution);
                    });

                    long writeCount = contribution.getWriteCount();
                    if (writeCount == 0) {
                        String msg = String.format("Arquivo processado com sucesso. Nenhuma nova matrícula cadastrada (%d matrículas já existiam no banco).",
                                ignoradasExistentes[0]);
                        executionContext.putString("warningMessage", msg);
                    } else if (ignoradasExistentes[0] > 0) {
                        String msg = String.format("Importação concluída com sucesso: %d novas matrículas cadastradas, %d ignoradas por já existirem.",
                                writeCount, ignoradasExistentes[0]);
                        executionContext.putString("warningMessage", msg);
                    }
                }
                case SALES -> {
                    String compParam = parameters.getString("competencia");
                    if (compParam == null || !compParam.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                        throw new BusinessException("Competência não informada ou inválida para importação de vendas.");
                    }
                    YearMonth compYm = YearMonth.parse(compParam);
                    List<InconsistenciaImportacao> inconsistencias = new ArrayList<>();

                    salesReader.forEach(input, row -> {
                        required(row.getRegistration(), "matrícula");
                        required(row.getStoreCode(), "código da loja");
                        required(row.getBrandCode(), "código da marca");
                        required(row.getReferenceDate(), "data de referência");
                        required(row.getSaleValue(), "valor da venda");

                        if (!YearMonth.from(row.getReferenceDate()).equals(compYm)) {
                            throw new BusinessException(String.format(
                                    "Linha com data de referência '%s' diverge da competência informada '%s' para o lote de vendas.",
                                    row.getReferenceDate(), compParam
                            ));
                        }

                        contribution.incrementReadCount();

                        Sale sale = saleMapper.toEntity(row);
                        if (sale == null) {
                            int numLinha = row.getLinha() > 0 ? row.getLinha() : (int) (contribution.getReadCount() + 1);
                            inconsistencias.add(new InconsistenciaImportacao(
                                    null,
                                    numLinha,
                                    "matricula",
                                    "Matrícula não cadastrada: " + row.getRegistration(),
                                    ImportInconsistencySeverity.ADVERTENCIA.name()
                            ));
                            return;
                        }
                        sale.setIdLoteOrigem(requestId);
                        sales.save(sale);
                        recordWrite(contribution);
                    });

                    if (contribution.getWriteCount() == 0) {
                        if (!inconsistencias.isEmpty()) {
                            InconsistenciaImportacao prim = inconsistencias.get(0);
                            throw new BusinessException("Linha " + prim.getLinha() + ": " + prim.getMotivo());
                        }
                        throw new BusinessException("Nenhuma venda válida encontrada no arquivo.");
                    }

                    if (!inconsistencias.isEmpty()) {
                        txService.salvarInconsistenciasPorIdLote(requestId, inconsistencias);
                        String msg = String.format("Importação concluída com advertências: %d vendas importadas com sucesso, %d linhas ignoradas por inconsistência.",
                                contribution.getWriteCount(), inconsistencias.size());
                        executionContext.putString("warningMessage", msg);
                    }
                }
                case COMISSIONS -> {
                    String compParam = parameters.getString("competencia");
                    if (compParam == null || !compParam.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                        throw new BusinessException("Competência não informada ou inválida para importação de comissões.");
                    }
                    LocalDate referenceMonth = YearMonth.parse(compParam).atDay(1);
                    Set<String> paresArquivo = new HashSet<>();

                    commissReader.forEach(input, row -> {
                        required(row.getBrandCode(), "código da marca");
                        required(row.getPositionCode(), "código do cargo");
                        required(row.getPercentage(), "percentual");

                        contribution.incrementReadCount();

                        String par = row.getBrandCode() + ":" + row.getPositionCode();
                        if (!paresArquivo.add(par)) {
                            throw new BusinessException(String.format(
                                    "Linha duplicada na planilha para marca %d e cargo %d na competência %s.",
                                    row.getBrandCode(), row.getPositionCode(), compParam
                            ));
                        }

                        if (commissions.existsByBrandCodeAndPositionCodeAndReferenceMonth(
                                row.getBrandCode(), row.getPositionCode(), referenceMonth)) {
                            throw new BusinessException(String.format(
                                    "Já existe taxa base cadastrada para a marca %d e cargo %d na competência %s.",
                                    row.getBrandCode(), row.getPositionCode(), compParam
                            ));
                        }

                        BaseComiss baseComiss = commissMapper.toEntity(row, referenceMonth);
                        baseComiss.setIdLoteOrigem(requestId);
                        commissions.save(baseComiss);
                        recordWrite(contribution);
                    });
                }
            }
        }
        entityManager.flush();
        return RepeatStatus.FINISHED;
    }

    private void recordWrite(StepContribution contribution) {
        contribution.incrementWriteCount(1);
        // Flush/clear bounds the persistence context; it does not commit the transaction.
        if (contribution.getWriteCount() % 100 == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }

    private static void required(Object value, String field) {
        if (value == null || value instanceof String text && text.isBlank()) {
            throw new BusinessException("Campo obrigatório ausente ou inválido: " + field);
        }
    }
}

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

import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.importbase.ImportType;
import com.bugbusters.backend.importbase.reader.*;
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

    public ImportTasklet(HrFileReader hrReader, SalesFileReader salesReader, CommissFileReader commissReader,
            RegistrationMapper registrationMapper, RegistrationRepository registrations,
            SaleMapper saleMapper, SaleRepository sales, BaseComissMapper commissMapper,
            BaseComissRepository commissions, EntityManager entityManager) {
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
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext context) throws Exception {
        var parameters = contribution.getStepExecution().getJobParameters();
        ImportType type = ImportType.valueOf(parameters.getString("importType"));
        try (InputStream input = Files.newInputStream(Path.of(parameters.getString("filePath")))) {
            switch (type) {
                case HR -> hrReader.forEach(input, row -> {
                    required(row.getRegistration(), "matrícula");
                    required(row.getStoreCode(), "código da loja");
                    required(row.getPositionCode(), "código do cargo");
                    required(row.getAdmissDate(), "data de admissão");
                    Registration mapped = registrationMapper.toEntity(row);
                    Registration target = registrations.findByRegistration(mapped.getRegistration())
                            .map(existing -> {
                                existing.setStore(mapped.getStore());
                                existing.setPosition(mapped.getPosition());
                                existing.setAdmissDate(mapped.getAdmissDate());
                                existing.setDemissDate(mapped.getDemissDate());
                                return existing;
                            })
                            .orElse(mapped);
                    registrations.save(target);
                    recordWrite(contribution);
                });
                case SALES -> salesReader.forEach(input, row -> {
                    required(row.getRegistration(), "matrícula");
                    required(row.getStoreCode(), "código da loja");
                    required(row.getBrandCode(), "código da marca");
                    required(row.getReferenceDate(), "data de referência");
                    required(row.getSaleValue(), "valor da venda");
                    Sale sale = saleMapper.toEntity(row);
                    if (sale == null) {
                        throw new BusinessException("Matrícula não cadastrada: " + row.getRegistration());
                    }
                    sale.setIdLoteOrigem(parameters.getString("requestId"));
                    sales.save(sale);
                    recordWrite(contribution);
                });
                case COMISSIONS -> commissReader.forEach(input, row -> {
                    required(row.getBrandCode(), "código da marca");
                    required(row.getPositionCode(), "código do cargo");
                    required(row.getPercentage(), "percentual");
                    commissions.save(commissMapper.toEntity(row));
                    recordWrite(contribution);
                });
            }
        }
        entityManager.flush();
        return RepeatStatus.FINISHED;
    }

    private void recordWrite(StepContribution contribution) {
        contribution.incrementReadCount();
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

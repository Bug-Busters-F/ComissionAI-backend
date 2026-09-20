package com.bugbusters.backend.importbase;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissMapper;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.importbase.dto.CommissFileRow;
import com.bugbusters.backend.importbase.dto.HrFileRow;
import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.importbase.reader.FileReader;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationMapper;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleMapper;
import com.bugbusters.backend.sales.SaleRepository;

@Service
public class ImportService {

    private final FileReaderFactory readerFactory;

    private final RegistrationMapper registrationMapper;
    private final RegistrationRepository registrationRepository;

    private final SaleMapper saleMapper;
    private final SaleRepository saleRepository;

    private final BaseComissMapper baseComissMapper;
    private final BaseComissRepository baseComissRepository;

    public ImportService(
            FileReaderFactory readerFactory,
            RegistrationMapper registrationMapper,
            RegistrationRepository registrationRepository,
            SaleMapper saleMapper,
            SaleRepository saleRepository,
            BaseComissMapper baseComissMapper,
            BaseComissRepository baseComissRepository
    ) {
        this.readerFactory = readerFactory;
        this.registrationMapper = registrationMapper;
        this.registrationRepository = registrationRepository;
        this.saleMapper = saleMapper;
        this.saleRepository = saleRepository;
        this.baseComissMapper = baseComissMapper;
        this.baseComissRepository = baseComissRepository;
    }

    public ImportResponse processImport(MultipartFile file, ImportType importType) {
        FileReader<?> reader = readerFactory.getFileReader(importType);

        try (InputStream input = file.getInputStream()) {
            List<?> dados = reader.read(input);

            int totalSalvo = switch (importType) {
                case HR -> saveHr(dados);
                case SALES -> saveSales(dados);
                case COMISSIONS -> saveCommissions(dados);
            };

            return new ImportResponse(
                    file.getOriginalFilename(),
                    importType,
                    totalSalvo
            );

        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo", e);
        }
    }

    @SuppressWarnings("unchecked")
    private int saveHr(List<?> dados) {
        List<HrFileRow> rows = (List<HrFileRow>) dados;

        List<Registration> entities = rows.stream()
                .map(registrationMapper::toEntity)
                .filter(Objects::nonNull)
                .toList();

        registrationRepository.saveAll(entities);
        return entities.size();
    }

    @SuppressWarnings("unchecked")
    private int saveSales(List<?> dados) {
        List<SalesFileRow> rows = (List<SalesFileRow>) dados;

        List<Sale> entities = rows.stream()
                .map(saleMapper::toEntity)
                .filter(Objects::nonNull)
                .toList();

        saleRepository.saveAll(entities);
        return entities.size();
    }

    @SuppressWarnings("unchecked")
    private int saveCommissions(List<?> dados) {
        List<CommissFileRow> rows = (List<CommissFileRow>) dados;

        List<BaseComiss> entities = rows.stream()
                .map(baseComissMapper::toEntity)
                .filter(Objects::nonNull)
                .toList();

        baseComissRepository.saveAll(entities);
        return entities.size();
    }
}
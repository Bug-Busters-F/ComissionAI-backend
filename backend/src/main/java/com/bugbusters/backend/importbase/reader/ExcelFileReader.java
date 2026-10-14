package com.bugbusters.backend.importbase.reader;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.importbase.util.ExcelUtils;

/** Iterates without creating a second in-memory copy of the worksheet as DTOs. */
public abstract class ExcelFileReader<T> implements FileReader<T> {
    protected abstract T map(Row row);

    @Override
    public void forEach(InputStream input, Consumer<T> consumer) {
        try (Workbook workbook = openWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException("Arquivo sem planilhas.");
            }
            int count = 0;
            for (Row row : workbook.getSheetAt(0)) {
                if (row.getRowNum() == 0 || ExcelUtils.isEmptyRow(row)) {
                    continue;
                }
                try {
                    consumer.accept(map(row));
                } catch (BusinessException | IllegalArgumentException ex) {
                    throw new BusinessException("Linha " + (row.getRowNum() + 1) + ": " + ex.getMessage());
                }
                count++;
            }
            if (count == 0) {
                throw new BusinessException("Arquivo sem linhas de dados.");
            }
        } catch (IOException ex) {
            throw new BusinessException("Não foi possível ler a planilha Excel.");
        }
    }

    private Workbook openWorkbook(InputStream input) {
        try {
            return WorkbookFactory.create(input);
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException("Arquivo Excel inválido, corrompido ou não suportado.");
        }
    }
}

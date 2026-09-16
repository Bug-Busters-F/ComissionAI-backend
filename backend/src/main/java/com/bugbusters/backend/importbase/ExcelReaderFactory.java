package com.bugbusters.backend.importbase;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.reader.CommissExcelReader;
import com.bugbusters.backend.importbase.reader.ExcelReader;
import com.bugbusters.backend.importbase.reader.HrExcelReader;
import com.bugbusters.backend.importbase.reader.SalesExcelReader;

@Component 
public class ExcelReaderFactory {
    private final HrExcelReader hrExcelReader;
    private final SalesExcelReader salesExcelReader;
    private final CommissExcelReader commissionsExcelReader;

    public ExcelReaderFactory(HrExcelReader hrExcelReader, SalesExcelReader salesExcelReader, CommissExcelReader commissionsExcelReader) {
        this.hrExcelReader = hrExcelReader;
        this.salesExcelReader = salesExcelReader;
        this.commissionsExcelReader = commissionsExcelReader;
    }

    public ExcelReader getExcelReader(ImportType importType) {
        return switch (importType) {
            case HR -> hrExcelReader;
            case SALES -> salesExcelReader;
            case COMISSIONS -> commissionsExcelReader;
        };
    }
}

package com.bugbusters.backend.importbase;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.reader.CommissExcelReader;
import com.bugbusters.backend.importbase.reader.FileReader;
import com.bugbusters.backend.importbase.reader.HrFileReader;
import com.bugbusters.backend.importbase.reader.SalesFileReader;

@Component 
public class FileReaderFactory {
    private final HrFileReader hrExcelReader;
    private final SalesFileReader salesExcelReader;
    private final CommissExcelReader commissionsExcelReader;

    public FileReaderFactory(HrFileReader hrExcelReader, SalesFileReader salesExcelReader, CommissExcelReader commissionsExcelReader) {
        this.hrExcelReader = hrExcelReader;
        this.salesExcelReader = salesExcelReader;
        this.commissionsExcelReader = commissionsExcelReader;
    }

    public FileReader<?> getFileReader(ImportType importType) {
        return switch (importType) {
            case HR -> hrExcelReader;
            case SALES -> salesExcelReader;
            case COMISSIONS -> commissionsExcelReader;
        };
    }
}

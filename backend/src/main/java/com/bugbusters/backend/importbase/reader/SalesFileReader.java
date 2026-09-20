package com.bugbusters.backend.importbase.reader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.importbase.mapper.SalesFileRowMapper;

@Component
public class SalesFileReader implements FileReader<SalesFileRow> {
    private SalesFileRowMapper mapper;

    private static final Logger log =
        LoggerFactory.getLogger(SalesFileReader.class);

    public SalesFileReader(SalesFileRowMapper mapper) {
        this.mapper = mapper;
    }

    public List<SalesFileRow> read(InputStream input) {
        List<SalesFileRow> rows = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(input)){
            Sheet sheet = workbook.getSheetAt(0);

            for(Row row : sheet) {
                if(row.getRowNum() == 0) continue;
                rows.add(mapper.toSalesFileRow(row));
            }

            return rows;
        } catch (Exception e) {
            log.error("Erro ao processar arquivo de vendas", e);
            throw new RuntimeException("Erro ao processar arquivo", e);
        }
    };
}

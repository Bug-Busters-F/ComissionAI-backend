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

import com.bugbusters.backend.importbase.dto.CommissFileRow;
import com.bugbusters.backend.importbase.mapper.CommissFileRowMapper;
import com.bugbusters.backend.importbase.util.ExcelUtils;

@Component
public class CommissFileReader implements FileReader<CommissFileRow> {
    private final CommissFileRowMapper mapper;

    private static final Logger log = LoggerFactory.getLogger(CommissFileReader.class);

    public CommissFileReader(CommissFileRowMapper mapper) {
        this.mapper = mapper;
    }

    @Override 
    public List<CommissFileRow> read(InputStream input){
        List<CommissFileRow> rows = new ArrayList<CommissFileRow>();

        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);

            for(Row row : sheet){
                if(row.getRowNum() == 0 || ExcelUtils.isEmptyRow(row)) {
                    continue;
                }

                rows.add(mapper.toCommissFileRow(row));
            }

            return rows;
        } catch (Exception e) {
            log.error("Erro ao processar arquivo de Comissão Final", e);
            throw new RuntimeException("Erro ao processar arquivo", e);
        }
    }
}

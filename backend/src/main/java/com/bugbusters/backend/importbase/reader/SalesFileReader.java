package com.bugbusters.backend.importbase.reader;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;
import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.importbase.mapper.SalesFileRowMapper;

@Component
public class SalesFileReader extends ExcelFileReader<SalesFileRow> {
    private final SalesFileRowMapper mapper;

    public SalesFileReader(SalesFileRowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected SalesFileRow map(Row row) {
        return mapper.toSalesFileRow(row);
    }
}

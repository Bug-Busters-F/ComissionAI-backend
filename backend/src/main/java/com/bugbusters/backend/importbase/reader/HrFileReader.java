package com.bugbusters.backend.importbase.reader;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;
import com.bugbusters.backend.importbase.dto.HrFileRow;
import com.bugbusters.backend.importbase.mapper.HrFileRowMapper;

@Component
public class HrFileReader extends ExcelFileReader<HrFileRow> {
    private final HrFileRowMapper mapper;

    public HrFileReader(HrFileRowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected HrFileRow map(Row row) {
        return mapper.toHrFileRow(row);
    }
}

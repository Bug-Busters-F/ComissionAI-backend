package com.bugbusters.backend.importbase.reader;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;
import com.bugbusters.backend.importbase.dto.CommissFileRow;
import com.bugbusters.backend.importbase.mapper.CommissFileRowMapper;

@Component
public class CommissFileReader extends ExcelFileReader<CommissFileRow> {
    private final CommissFileRowMapper mapper;

    public CommissFileReader(CommissFileRowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected CommissFileRow map(Row row) {
        return mapper.toCommissFileRow(row);
    }
}

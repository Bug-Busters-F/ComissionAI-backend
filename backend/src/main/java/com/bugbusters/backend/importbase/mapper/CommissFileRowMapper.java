package com.bugbusters.backend.importbase.mapper;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.CommissFileRow;
import com.bugbusters.backend.importbase.util.CellUtils;

@Component
public class CommissFileRowMapper {
    public CommissFileRow toCommissFileRow(Row row) {
        return new CommissFileRow(
                CellUtils.getIntCell(row, 0),
                CellUtils.getStringCell(row, 1),
                CellUtils.getIntCell(row, 2),
                CellUtils.getStringCell(row, 3),
                CellUtils.getBigDecimalCell(row, 4));
    }
}

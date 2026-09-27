package com.bugbusters.backend.importbase.mapper;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.HrFileRow;
import com.bugbusters.backend.importbase.util.CellUtils;

@Component
public class HrFileRowMapper {

    public HrFileRow toHrFileRow(Row row) {
        
        return new HrFileRow(
                CellUtils.getLocalDateCell(row, 0),
                CellUtils.getIntCell(row, 1),
                CellUtils.getStringCell(row, 2),
                CellUtils.getIntCell(row, 3),
                CellUtils.getStringCell(row, 4),
                CellUtils.getStringCell(row, 5),
                CellUtils.getLocalDateCell(row, 6),
                CellUtils.getLocalDateCell(row, 7),
                CellUtils.getIntCell(row, 8),
                CellUtils.getStringCell(row,9)
        );
    }
}
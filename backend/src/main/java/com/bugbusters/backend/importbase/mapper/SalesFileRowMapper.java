package com.bugbusters.backend.importbase.mapper;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.importbase.util.CellUtils;

@Component
public class SalesFileRowMapper {
    public SalesFileRow toSalesFileRow(Row row) {
        return new SalesFileRow(
                CellUtils.getLocalDateCell(row, 0),
                CellUtils.getIntCell(row, 1),
                CellUtils.getStringCell(row, 2),
                CellUtils.getIntCell(row, 3),
                CellUtils.getStringCell(row, 4),
                CellUtils.getStringCell(row, 5),
                CellUtils.getBigDecimalCell(row, 6));
    };
}

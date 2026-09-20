package com.bugbusters.backend.importbase.mapper;

import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.CommissFileRow;

@Component 
public class CommissFileRowMapper {
    public CommissFileRow toCommissFileRow(Row row) {
          Integer brandCode = (int) row.getCell(0).getNumericCellValue();
          Integer positionCode = (int) row.getCell(2).getNumericCellValue();
          BigDecimal percent = BigDecimal.valueOf(row.getCell(4).getNumericCellValue());

        return new CommissFileRow(
            brandCode,
            row.getCell(1).getStringCellValue(),
            positionCode,
            row.getCell(3).getStringCellValue(),
            percent
        );
    }
}

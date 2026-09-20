package com.bugbusters.backend.importbase.mapper;

import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.SalesFileRow;

@Component
public class SalesFileRowMapper {
    public SalesFileRow toSalesFileRow(Row row) {
        Integer brandCode = (int) row.getCell(1).getNumericCellValue();
        Integer storeCode = (int) row.getCell(3).getNumericCellValue();
        BigDecimal saleValue = BigDecimal.valueOf(row.getCell(6).getNumericCellValue());

        return new SalesFileRow(
            row.getCell(0).getLocalDateTimeCellValue().toLocalDate(), 
            brandCode, 
            row.getCell(2).getStringCellValue(), 
            storeCode, 
            row.getCell(4).getStringCellValue(),
            row.getCell(5).getStringCellValue(),
            saleValue
        );
    };
}

package com.bugbusters.backend.importbase.util;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

public class CellUtils {
    public static Integer getIntCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() != CellType.NUMERIC)
            return null;
        return (int) cell.getNumericCellValue();
    }

    public static String getStringCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null)
            return null;
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue().trim() : null;
    }

    public static BigDecimal getBigDecimalCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() != CellType.NUMERIC)
            return null;
        return BigDecimal.valueOf(cell.getNumericCellValue());
    }

    public static LocalDate getLocalDateCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null)
            return null;

        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getLocalDateTimeCellValue().toLocalDate();
            case STRING -> DateUtils.parseMonthYear(cell.getStringCellValue());
            case BLANK -> null;
            default -> throw new IllegalArgumentException(
                    "Tipo de célula inesperado para data: " + cell.getCellType());
        };
    }
}

package com.bugbusters.backend.importbase.util;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

public class CellUtils {
    public static Integer getIntCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.NUMERIC)
            return (int) cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Integer.parseInt(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static String getStringCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
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
            case STRING -> DateUtils.parseDate(cell.getStringCellValue());
            case BLANK -> null;
            default -> throw new IllegalArgumentException(
                    "Tipo de célula inesperado para data: " + cell.getCellType());
        };
    }
}

package com.bugbusters.backend.importbase.mapper;

import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.HrFileRow;
import com.bugbusters.backend.importbase.util.DateUtils;

@Component
public class HrFileRowMapper {

    public HrFileRow toHrFileRow(Row row) {

        Cell referenceDateCell = row.getCell(0);

        LocalDate referenceDate;

        if (referenceDateCell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(referenceDateCell)) {

            referenceDate = referenceDateCell
                    .getLocalDateTimeCellValue()
                    .toLocalDate();

        } else {

            referenceDate = DateUtils.parseMonthYear(
                    referenceDateCell.getStringCellValue()
            );
        }

        Integer brandCode = (int) row.getCell(1).getNumericCellValue();

        Integer storeCode = (int) row.getCell(3).getNumericCellValue();

        LocalDate admissDate = row.getCell(6)
                .getLocalDateTimeCellValue()
                .toLocalDate();

        Cell demissDateCell = row.getCell(7);

        LocalDate demissDate = null;

        if (demissDateCell != null
                && demissDateCell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(demissDateCell)) {

            demissDate = demissDateCell
                    .getLocalDateTimeCellValue()
                    .toLocalDate();
        }

        Integer positionCode = (int) row.getCell(8).getNumericCellValue();

        return new HrFileRow(
                referenceDate,
                brandCode,
                row.getCell(2).getStringCellValue(),
                storeCode,
                row.getCell(4).getStringCellValue(),
                row.getCell(5).getStringCellValue(),
                admissDate,
                demissDate,
                positionCode,
                row.getCell(9).getStringCellValue()
        );
    }
}
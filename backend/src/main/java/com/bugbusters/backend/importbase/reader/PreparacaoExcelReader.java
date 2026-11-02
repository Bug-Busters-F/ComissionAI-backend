package com.bugbusters.backend.importbase.reader;

import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.importbase.util.CellUtils;
import com.bugbusters.backend.importbase.util.DateUtils;
import com.bugbusters.backend.importbase.util.ExcelUtils;
import com.bugbusters.backend.importbase.validation.ErroValidacaoLinhaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
public class PreparacaoExcelReader {

    private final ObjectMapper objectMapper;

    public PreparacaoExcelReader() {
        this(new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
    }

    public PreparacaoExcelReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    public record LinhaLidaPreparacao(
            int numeroLinha,
            String conteudoOriginalJson,
            LocalDate dataReferencia,
            Integer codMarca,
            String descrMarca,
            Integer codLoja,
            String descrLoja,
            String matricula,
            LocalDate dataAdmissao,
            LocalDate dataDemissao,
            Integer codCargo,
            String descrCargo,
            BigDecimal valorVenda,
            List<ErroValidacaoLinhaDTO> errosDeFormato
    ) {}

    public List<LinhaLidaPreparacao> lerLinhas(InputStream input, String tipoBase) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException("Arquivo sem planilhas.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            List<LinhaLidaPreparacao> linhas = new ArrayList<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0 || ExcelUtils.isEmptyRow(row)) {
                    continue;
                }
                int numLinha = row.getRowNum() + 1;
                Map<String, String> rawMap = new LinkedHashMap<>();
                List<ErroValidacaoLinhaDTO> errosFormato = new ArrayList<>();

                short lastCellNum = row.getLastCellNum();
                for (int col = 0; col < Math.max(lastCellNum, 10); col++) {
                    Cell c = row.getCell(col);
                    String val = c != null ? CellUtils.getStringCell(row, col) : null;
                    if (val != null) {
                        rawMap.put("col_" + col, val);
                    }
                }

                LocalDate dataReferencia = null;
                Integer codMarca = null;
                String descrMarca = null;
                Integer codLoja = null;
                String descrLoja = null;
                String matricula = null;
                LocalDate dataAdmissao = null;
                LocalDate dataDemissao = null;
                Integer codCargo = null;
                String descrCargo = null;
                BigDecimal valorVenda = null;

                if ("HR".equalsIgnoreCase(tipoBase)) {
                    dataReferencia = parseLocalDateTolerante(row, 0, "dataReferencia", rawMap, errosFormato);
                    codMarca = parseIntTolerante(row, 1, "codMarca", rawMap, errosFormato);
                    descrMarca = parseString(row, 2, "descrMarca", rawMap);
                    codLoja = parseIntTolerante(row, 3, "codLoja", rawMap, errosFormato);
                    descrLoja = parseString(row, 4, "descrLoja", rawMap);
                    matricula = parseString(row, 5, "matricula", rawMap);
                    dataAdmissao = parseLocalDateTolerante(row, 6, "dataAdmissao", rawMap, errosFormato);
                    dataDemissao = parseLocalDateTolerante(row, 7, "dataDemissao", rawMap, errosFormato);
                    codCargo = parseIntTolerante(row, 8, "codCargo", rawMap, errosFormato);
                    descrCargo = parseString(row, 9, "descrCargo", rawMap);
                } else if ("SALES".equalsIgnoreCase(tipoBase)) {
                    dataReferencia = parseLocalDateTolerante(row, 0, "referenceDate", rawMap, errosFormato);
                    codMarca = parseIntTolerante(row, 1, "brandCode", rawMap, errosFormato);
                    descrMarca = parseString(row, 2, "descrMarca", rawMap);
                    codLoja = parseIntTolerante(row, 3, "storeCode", rawMap, errosFormato);
                    descrLoja = parseString(row, 4, "descrLoja", rawMap);
                    matricula = parseString(row, 5, "matricula", rawMap);
                    valorVenda = parseBigDecimalTolerante(row, 6, "saleValue", rawMap, errosFormato);
                }

                String rawJson = objectMapper.writeValueAsString(rawMap);

                linhas.add(new LinhaLidaPreparacao(
                        numLinha,
                        rawJson,
                        dataReferencia,
                        codMarca,
                        descrMarca,
                        codLoja,
                        descrLoja,
                        matricula,
                        dataAdmissao,
                        dataDemissao,
                        codCargo,
                        descrCargo,
                        valorVenda,
                        errosFormato
                ));
            }

            if (linhas.isEmpty()) {
                throw new BusinessException("Arquivo sem linhas de dados.");
            }

            return linhas;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            throw new BusinessException("Não foi possível ler a planilha Excel: " + ex.getMessage());
        }
    }

    private String parseString(Row row, int col, String campoNome, Map<String, String> rawMap) {
        String s = CellUtils.getStringCell(row, col);
        if (s != null) {
            rawMap.put(campoNome, s);
            return s.trim();
        }
        return null;
    }

    private Integer parseIntTolerante(Row row, int col, String campoNome, Map<String, String> rawMap, List<ErroValidacaoLinhaDTO> erros) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                int val = (int) cell.getNumericCellValue();
                rawMap.put(campoNome, String.valueOf(val));
                return val;
            }
            if (cell.getCellType() == CellType.STRING) {
                String str = cell.getStringCellValue().trim();
                rawMap.put(campoNome, str);
                if (str.isBlank()) return null;
                return Integer.parseInt(str);
            }
        } catch (Exception ex) {
            String str = CellUtils.getStringCell(row, col);
            rawMap.put(campoNome, str);
            erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Valor numérico inteiro inválido para o campo " + campoNome + ": '" + str + "'"));
        }
        return null;
    }

    private BigDecimal parseBigDecimalTolerante(Row row, int col, String campoNome, Map<String, String> rawMap, List<ErroValidacaoLinhaDTO> erros) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                BigDecimal val = BigDecimal.valueOf(cell.getNumericCellValue());
                rawMap.put(campoNome, val.toPlainString());
                return val;
            }
            if (cell.getCellType() == CellType.STRING) {
                String str = cell.getStringCellValue().trim();
                rawMap.put(campoNome, str);
                if (str.isBlank()) return null;
                str = str.replace(",", ".");
                return new BigDecimal(str);
            }
        } catch (Exception ex) {
            String str = CellUtils.getStringCell(row, col);
            rawMap.put(campoNome, str);
            erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Valor numérico decimal inválido para o campo " + campoNome + ": '" + str + "'"));
        }
        return null;
    }

    private LocalDate parseLocalDateTolerante(Row row, int col, String campoNome, Map<String, String> rawMap, List<ErroValidacaoLinhaDTO> erros) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                rawMap.put(campoNome, date.toString());
                return date;
            }
            if (cell.getCellType() == CellType.STRING) {
                String str = cell.getStringCellValue().trim();
                rawMap.put(campoNome, str);
                if (str.isBlank()) return null;
                return DateUtils.parseDate(str);
            }
        } catch (Exception ex) {
            String str = CellUtils.getStringCell(row, col);
            rawMap.put(campoNome, str);
            erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Formato de data inválido para o campo " + campoNome + ": '" + str + "'"));
        }
        return null;
    }
}

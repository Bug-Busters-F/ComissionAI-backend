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
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
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
                    dataReferencia = parseLocalDateTolerante(row, 0, "dataReferencia", rawMap, errosFormato, evaluator);
                    codMarca = parseIntTolerante(row, 1, "codMarca", rawMap, errosFormato, evaluator);
                    descrMarca = parseString(row, 2, "descrMarca", rawMap, evaluator);
                    codLoja = parseIntTolerante(row, 3, "codLoja", rawMap, errosFormato, evaluator);
                    descrLoja = parseString(row, 4, "descrLoja", rawMap, evaluator);
                    matricula = parseString(row, 5, "matricula", rawMap, evaluator);
                    dataAdmissao = parseLocalDateTolerante(row, 6, "dataAdmissao", rawMap, errosFormato, evaluator);
                    dataDemissao = parseLocalDateTolerante(row, 7, "dataDemissao", rawMap, errosFormato, evaluator);
                    codCargo = parseIntTolerante(row, 8, "codCargo", rawMap, errosFormato, evaluator);
                    descrCargo = parseString(row, 9, "descrCargo", rawMap, evaluator);
                } else if ("SALES".equalsIgnoreCase(tipoBase)) {
                    dataReferencia = parseLocalDateTolerante(row, 0, "referenceDate", rawMap, errosFormato, evaluator);
                    codMarca = parseIntTolerante(row, 1, "brandCode", rawMap, errosFormato, evaluator);
                    descrMarca = parseString(row, 2, "descrMarca", rawMap, evaluator);
                    codLoja = parseIntTolerante(row, 3, "storeCode", rawMap, errosFormato, evaluator);
                    descrLoja = parseString(row, 4, "descrLoja", rawMap, evaluator);
                    matricula = parseString(row, 5, "matricula", rawMap, evaluator);
                    valorVenda = parseBigDecimalTolerante(row, 6, "saleValue", rawMap, errosFormato, evaluator);
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

    private String parseString(Row row, int col, String campoNome, Map<String, String> rawMap, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.FORMULA) {
                CellValue cv = avaliarCelula(cell, evaluator);
                if (cv != null) {
                    if (cv.getCellType() == CellType.STRING) {
                        String s = cv.getStringValue().trim();
                        rawMap.put(campoNome, s);
                        return s;
                    } else if (cv.getCellType() == CellType.NUMERIC) {
                        String s = String.valueOf((long) cv.getNumberValue());
                        rawMap.put(campoNome, s);
                        return s;
                    }
                }
            }
        } catch (Exception ignored) {}
        String s = CellUtils.getStringCell(row, col);
        if (s != null) {
            rawMap.put(campoNome, s);
            return s.trim();
        }
        return null;
    }

    private Integer parseIntTolerante(Row row, int col, String campoNome, Map<String, String> rawMap, List<ErroValidacaoLinhaDTO> erros, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                CellValue cv = avaliarCelula(cell, evaluator);
                if (cv != null) {
                    if (cv.getCellType() == CellType.ERROR) {
                        erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Fórmula com erro de cálculo no campo " + campoNome));
                        return null;
                    }
                    if (cv.getCellType() == CellType.NUMERIC) {
                        int val = (int) cv.getNumberValue();
                        rawMap.put(campoNome, String.valueOf(val));
                        return val;
                    }
                    if (cv.getCellType() == CellType.STRING) {
                        String str = cv.getStringValue().trim();
                        rawMap.put(campoNome, str);
                        if (str.isBlank()) return null;
                        return Integer.parseInt(str);
                    }
                    if (cv.getCellType() == CellType.BOOLEAN) {
                        erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Fórmula retornou tipo booleano incompatível no campo " + campoNome));
                        return null;
                    }
                }
                type = cell.getCachedFormulaResultType();
            }
            if (type == CellType.NUMERIC) {
                int val = (int) cell.getNumericCellValue();
                rawMap.put(campoNome, String.valueOf(val));
                return val;
            }
            if (type == CellType.STRING) {
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

    private BigDecimal parseBigDecimalTolerante(Row row, int col, String campoNome, Map<String, String> rawMap, List<ErroValidacaoLinhaDTO> erros, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                CellValue cv = avaliarCelula(cell, evaluator);
                if (cv != null) {
                    if (cv.getCellType() == CellType.ERROR) {
                        erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Fórmula com erro de cálculo no campo " + campoNome));
                        return null;
                    }
                    if (cv.getCellType() == CellType.NUMERIC) {
                        BigDecimal val = BigDecimal.valueOf(cv.getNumberValue());
                        rawMap.put(campoNome, val.toPlainString());
                        return val;
                    }
                    if (cv.getCellType() == CellType.STRING) {
                        String str = cv.getStringValue().trim();
                        rawMap.put(campoNome, str);
                        if (str.isBlank()) return null;
                        str = str.replace(",", ".");
                        return new BigDecimal(str);
                    }
                    if (cv.getCellType() == CellType.BOOLEAN) {
                        erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Fórmula retornou tipo booleano incompatível no campo " + campoNome));
                        return null;
                    }
                }
                type = cell.getCachedFormulaResultType();
            }
            if (type == CellType.NUMERIC) {
                BigDecimal val = BigDecimal.valueOf(cell.getNumericCellValue());
                rawMap.put(campoNome, val.toPlainString());
                return val;
            }
            if (type == CellType.STRING) {
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

    private LocalDate parseLocalDateTolerante(Row row, int col, String campoNome, Map<String, String> rawMap, List<ErroValidacaoLinhaDTO> erros, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                CellValue cv = avaliarCelula(cell, evaluator);
                if (cv != null) {
                    if (cv.getCellType() == CellType.ERROR) {
                        erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Fórmula com erro de cálculo no campo " + campoNome));
                        return null;
                    }
                    if (cv.getCellType() == CellType.NUMERIC) {
                        LocalDate date;
                        if (DateUtil.isCellDateFormatted(cell)) {
                            date = cell.getLocalDateTimeCellValue().toLocalDate();
                        } else {
                            date = DateUtil.getLocalDateTime(cv.getNumberValue()).toLocalDate();
                        }
                        rawMap.put(campoNome, date.toString());
                        return date;
                    }
                    if (cv.getCellType() == CellType.STRING) {
                        String str = cv.getStringValue().trim();
                        rawMap.put(campoNome, str);
                        if (str.isBlank()) return null;
                        return DateUtils.parseDate(str);
                    }
                    if (cv.getCellType() == CellType.BOOLEAN) {
                        erros.add(ErroValidacaoLinhaDTO.impeditivo(campoNome, "Fórmula retornou tipo booleano incompatível no campo " + campoNome));
                        return null;
                    }
                }
                type = cell.getCachedFormulaResultType();
            }
            if (type == CellType.NUMERIC) {
                LocalDate date;
                if (DateUtil.isCellDateFormatted(cell)) {
                    date = cell.getLocalDateTimeCellValue().toLocalDate();
                } else {
                    date = DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
                }
                rawMap.put(campoNome, date.toString());
                return date;
            }
            if (type == CellType.STRING) {
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

    private CellValue avaliarCelula(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null || cell.getCellType() != CellType.FORMULA || evaluator == null) {
            return null;
        }
        try {
            return evaluator.evaluate(cell);
        } catch (Exception ex) {
            return null;
        }
    }
}

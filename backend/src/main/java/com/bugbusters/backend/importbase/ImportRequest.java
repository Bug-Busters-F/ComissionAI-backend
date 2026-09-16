package com.bugbusters.backend.importbase;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Relatório de validação e processamento de upload de planilhas/CSVs")
public class ImportRequest {
    @Schema(description = "Arquivo submetido para validação e processamento", example = "BASE_VENDAS_OUT25.csv")
    MultipartFile file;

    @Schema(description = "Tipo de base submetida", example = "SALES")
    ImportType importType;

    public MultipartFile getFile() {
        return file;
    }

    public ImportType getImportType() {
        return importType;
    }
}

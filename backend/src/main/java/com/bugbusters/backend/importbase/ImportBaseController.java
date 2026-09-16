package com.bugbusters.backend.importbase;

import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Ingestão de Dados", description = "Upload e validação estrutural de planilhas/CSVs (RH, Vendas, Comissões)")
public class ImportBaseController {
    private final ImportService importService;

    public ImportBaseController(ImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "Upload de base de dados com validação", description = "Processa o arquivo, identifica falhas impeditivas ou avisos e rejeita integralmente caso ocorra duplicidade ou ausência de campos chave.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> upload(
            @RequestBody ImportRequest request
        ) {


            return this.importService.processImport(request);
    }
}
package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.preparacao.*;
import com.bugbusters.backend.model.ArquivoImportacao;
import com.bugbusters.backend.service.preparacao.PreparacaoBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campanhas/{campanhaId}/preparacao-bases")
@Tag(name = "Preparação de Bases por Campanha", description = "Área de preparação, validação e edição de bases globais de RH e Vendas vinculadas à campanha (BACK-5)")
public class CampanhaPreparacaoBaseController {

    private final PreparacaoBaseService preparacaoBaseService;

    public CampanhaPreparacaoBaseController(PreparacaoBaseService preparacaoBaseService) {
        this.preparacaoBaseService = preparacaoBaseService;
    }

    @Operation(summary = "Enviar arquivo para área de preparação", description = "Recebe planilha Excel de RH ou Vendas e persiste todas as linhas, inclusive inválidas, com diagnóstico de validação.")
    @ApiResponse(responseCode = "201", description = "Lote em preparação criado com sucesso")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LotePreparacaoResponseDTO> upload(
            @PathVariable Long campanhaId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("tipoBase") String tipoBase,
            @RequestParam(value = "competencia", required = false) String competencia,
            @RequestParam(value = "origem", required = false, defaultValue = "MANUAL") String origem,
            @RequestParam(value = "reportIdSubstituido", required = false) Long reportIdSubstituido,
            @RequestParam(value = "responsavel", required = false) String responsavel) {

        LotePreparacaoResponseDTO response = preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaId, file, tipoBase, competencia, origem, reportIdSubstituido, responsavel);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Iniciar preparação a partir de lote existente", description = "Abre em preparação um lote já importado no sistema para correção de suas inconsistências.")
    @ApiResponse(responseCode = "201", description = "Lote de preparação aberto a partir do lote anterior")
    @PostMapping("/a-partir-de-lote/{reportId}")
    public ResponseEntity<LotePreparacaoResponseDTO> aPartirDeLote(
            @PathVariable Long campanhaId,
            @PathVariable Long reportId,
            @RequestParam(value = "responsavel", required = false) String responsavel) {

        LotePreparacaoResponseDTO response = preparacaoBaseService.iniciarPreparacaoDeLoteExistente(
                campanhaId, reportId, responsavel);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Listar lotes de preparação da campanha", description = "Lista os lotes de preparação criados para a campanha.")
    @GetMapping
    public ResponseEntity<List<LotePreparacaoResponseDTO>> listarLotes(
            @PathVariable Long campanhaId,
            @RequestParam(required = false) String tipoBase,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) String status) {

        List<LotePreparacaoResponseDTO> response = preparacaoBaseService.listarLotes(
                campanhaId, tipoBase, competencia, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consultar resumo do lote de preparação")
    @GetMapping("/{preparacaoId}")
    public ResponseEntity<LotePreparacaoResponseDTO> consultarLote(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId) {

        return ResponseEntity.ok(preparacaoBaseService.consultarLote(campanhaId, preparacaoId));
    }

    @Operation(summary = "Consultar linhas do lote de preparação de forma paginada e filtrada")
    @GetMapping("/{preparacaoId}/linhas")
    public ResponseEntity<Page<LinhaPreparacaoResponseDTO>> listarLinhas(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId,
            @RequestParam(required = false) Boolean valida,
            @RequestParam(required = false) String severidade,
            @RequestParam(required = false) String campoErro,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) Boolean editada,
            @RequestParam(required = false) Boolean ignorada,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        FiltroLinhaPreparacaoDTO filtro = new FiltroLinhaPreparacaoDTO(
                valida, severidade, campoErro, matricula, dataInicio, dataFim, editada, ignorada);
        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(preparacaoBaseService.listarLinhas(campanhaId, preparacaoId, filtro, pageable));
    }

    @Operation(summary = "Editar uma linha do lote de preparação", description = "Edita valores da linha com bloqueio otimista via versão e revalida imediatamente.")
    @PatchMapping("/{preparacaoId}/linhas/{numeroLinha}")
    public ResponseEntity<LinhaPreparacaoResponseDTO> editarLinha(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId,
            @PathVariable int numeroLinha,
            @RequestBody EditarLinhaPreparacaoRequestDTO request) {

        return ResponseEntity.ok(preparacaoBaseService.editarLinha(campanhaId, preparacaoId, numeroLinha, request));
    }

    @Operation(summary = "Editar múltiplas linhas do lote em massa")
    @PatchMapping("/{preparacaoId}/linhas")
    public ResponseEntity<LotePreparacaoResponseDTO> editarLinhasEmLote(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId,
            @RequestBody EditarLinhasLoteRequestDTO request) {

        return ResponseEntity.ok(preparacaoBaseService.editarLinhasEmLote(campanhaId, preparacaoId, request));
    }

    @Operation(summary = "Consultar histórico de edições de uma linha")
    @GetMapping("/{preparacaoId}/linhas/{numeroLinha}/historico")
    public ResponseEntity<List<HistoricoEdicaoLinhaResponseDTO>> consultarHistoricoLinha(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId,
            @PathVariable int numeroLinha) {

        return ResponseEntity.ok(preparacaoBaseService.consultarHistoricoEdicaoLinha(campanhaId, preparacaoId, numeroLinha));
    }

    @Operation(summary = "Revalidar todas as linhas do lote")
    @PostMapping("/{preparacaoId}/revalidar")
    public ResponseEntity<LotePreparacaoResponseDTO> revalidarLote(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId,
            @RequestParam(required = false) String responsavel) {

        return ResponseEntity.ok(preparacaoBaseService.revalidarLote(campanhaId, preparacaoId, responsavel));
    }

    @Operation(summary = "Efetivar lote de preparação na base geral", description = "Grava as alterações em tb_registration ou tb_sales com versionamento, gera o EnvioArquivo e anexa à campanha.")
    @PostMapping("/{preparacaoId}/efetivar")
    public ResponseEntity<EfetivacaoPreparacaoResponseDTO> efetivarLote(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId,
            @RequestBody(required = false) EfetivarLotePreparacaoRequestDTO request) {

        return ResponseEntity.ok(preparacaoBaseService.efetivarLote(campanhaId, preparacaoId, request));
    }

    @Operation(summary = "Descartar lote de preparação")
    @DeleteMapping("/{preparacaoId}")
    public ResponseEntity<LotePreparacaoResponseDTO> descartarLote(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) String responsavel) {

        return ResponseEntity.ok(preparacaoBaseService.descartarLote(campanhaId, preparacaoId, motivo, responsavel));
    }

    @Operation(summary = "Baixar arquivo original preservado")
    @GetMapping("/{preparacaoId}/arquivo-original")
    public ResponseEntity<byte[]> baixarArquivoOriginal(
            @PathVariable Long campanhaId,
            @PathVariable UUID preparacaoId) {

        ArquivoImportacao arq = preparacaoBaseService.baixarArquivoOriginal(campanhaId, preparacaoId);
        String contentType = arq.getContentType() != null ? arq.getContentType() : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + arq.getNomeArquivo() + "\"")
                .body(arq.getConteudo());
    }

    @Operation(summary = "Consultar histórico de versões de um registro na base geral")
    @GetMapping("/historico-registros")
    public ResponseEntity<List<HistoricoVersaoRegistroResponseDTO>> consultarHistoricoRegistro(
            @PathVariable Long campanhaId,
            @RequestParam String tipoBase,
            @RequestParam String chaveOuId) {

        return ResponseEntity.ok(preparacaoBaseService.consultarHistoricoRegistro(tipoBase, chaveOuId));
    }
}

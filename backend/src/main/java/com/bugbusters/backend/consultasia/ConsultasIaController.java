package com.bugbusters.backend.consultasia;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.consultasia.ConsultasIaDTOs.BaseComissaoResponse;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.CatalogoResponse;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.MatriculaResponse;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.PaginaDTO;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.VendaSimulacaoDTO;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.RegrasConcorrentesRequest;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.RegrasConcorrentesResponse;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.ConsistenciaRecorteIaResponse;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.RecortePopulacaoIaRequest;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.RecortePopulacaoIaResponse;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.VerificarConsistenciaRecorteIaRequest;
import com.bugbusters.backend.dto.error.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/consultas-ia")
@Tag(name = "10. Consultas para a IA", description = """
        Consultas usadas pelas ferramentas do agente de IA e simulações de comissão. Consulta válida sem resultado \
        responde HTTP 200 com `encontrado=false` (ausência de informação); erro real responde 4xx/5xx no formato ApiErrorResponse.""")
public class ConsultasIaController {

    private final ConsultasIaService service;
    private final RecortePopulacaoIaService recorteService;

    public ConsultasIaController(ConsultasIaService service, RecortePopulacaoIaService recorteService) {
        this.service = service;
        this.recorteService = recorteService;
    }

    @Operation(summary = "Consultar marcas", description = "Filtra por código (exato) e/ou nome (normalizado: maiúsculas, sem acento, contém). Vários itens indicam ambiguidade.")
    @ApiResponse(responseCode = "200", description = "Consulta realizada (encontrado=false quando não há itens)")
    @GetMapping("/marcas")
    public ResponseEntity<CatalogoResponse> marcas(
            @Parameter(description = "Nome ou parte do nome") @RequestParam(required = false) String nome,
            @Parameter(description = "Código exato") @RequestParam(required = false) Integer codigo) {
        return ResponseEntity.ok(service.buscarMarcas(nome, codigo));
    }

    @Operation(summary = "Consultar lojas", description = "Filtra por código (exato) e/ou nome (normalizado). `codMarca` vem nulo porque a base não relaciona loja e marca.")
    @ApiResponse(responseCode = "200", description = "Consulta realizada (encontrado=false quando não há itens)")
    @GetMapping("/lojas")
    public ResponseEntity<CatalogoResponse> lojas(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Integer codigo) {
        return ResponseEntity.ok(service.buscarLojas(nome, codigo));
    }

    @Operation(summary = "Consultar cargos", description = "Filtra por código (exato) e/ou nome (normalizado). Vários itens indicam ambiguidade de cargo.")
    @ApiResponse(responseCode = "200", description = "Consulta realizada (encontrado=false quando não há itens)")
    @GetMapping("/cargos")
    public ResponseEntity<CatalogoResponse> cargos(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Integer codigo) {
        return ResponseEntity.ok(service.buscarCargos(nome, codigo));
    }

    @Operation(summary = "Consultar vínculo de uma matrícula", description = "Devolve loja, cargo e datas de admissão/demissão. Matrícula inexistente responde apenas `{\"encontrado\":false}`.")
    @ApiResponse(responseCode = "200", description = "Consulta realizada")
    @GetMapping("/matriculas/{matricula}")
    public ResponseEntity<MatriculaResponse> matricula(@PathVariable String matricula) {
        return ResponseEntity.ok(service.buscarMatricula(matricula));
    }

    @Operation(summary = "Consultar taxa base por marca, cargo e competência", description = "Devolve a taxa base da competência; se não houver para o mês, usa o mais recente disponível e informa em `competenciaUsada`.")
    @ApiResponse(responseCode = "200", description = "Consulta realizada (encontrado=false quando não há base)")
    @ApiResponse(responseCode = "400", description = "Parâmetros ausentes ou competência inválida", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/bases")
    public ResponseEntity<BaseComissaoResponse> bases(
            @RequestParam(required = false) Integer codMarca,
            @RequestParam(required = false) Integer codCargo,
            @Parameter(description = "Competência no formato YYYY-MM", example = "2026-09") @RequestParam(required = false) String competencia) {
        return ResponseEntity.ok(service.buscarBase(codMarca, codCargo, competencia));
    }

    @Operation(summary = "Consultar regras ativas concorrentes", description = """
            Lista regras ATIVAS cuja vigência, público e faixa podem coincidir com a proposta, com o peso de especificidade \
            (matrícula 16, loja 8, cargo 4, canal 2, marca 1) definido em PRECEDENCIA_E_SELECAO_TAXAS.md. \
            `ignorarCampanhaId` exclui as regras da campanha que a proposta substituirá. Não calcula valores.""")
    @ApiResponse(responseCode = "200", description = "Consulta realizada (lista vazia quando não há concorrência)")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/regras-concorrentes")
    public ResponseEntity<RegrasConcorrentesResponse> regrasConcorrentes(@RequestBody RegrasConcorrentesRequest request) {
        return ResponseEntity.ok(service.buscarRegrasConcorrentes(request));
    }

    @Operation(summary = "Fornecer recorte completo e reproduzível da população para simulação e cálculo da IA",
            description = """
                    Monta o JSON unificado da população selecionada para a campanha informada ou faixa de datas, \
                    incluindo todas as vendas, dados de RH vigentes, taxas contratuais (tb_basecomiss), regras da campanha, \
                    regras concorrentes ativas e pendências aceitas necessárias ao cálculo. \
                    Identifica versões das fontes e totais de registros, computa hashes SHA-256 canônicos e impede \
                    recortes silenciosamente incompletos (rejeitando com HTTP 422). Caso seja fornecido hash ou versão esperados, \
                    garante consistência detectando mutações concorrentes com HTTP 409.""")
    @ApiResponse(responseCode = "200", description = "Recorte gerado com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros ausentes ou inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha informada não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Conflito de concorrência ou mutação nos dados da população", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Recorte incompleto ou inconsistente para cálculo", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/recorte-populacao")
    public ResponseEntity<RecortePopulacaoIaResponse> gerarRecortePopulacao(@RequestBody RecortePopulacaoIaRequest request) {
        return ResponseEntity.ok(recorteService.obterRecorte(request));
    }

    @Operation(summary = "Consultar recorte completo por GET (inspeção rápida)",
            description = "Equivalente ao endpoint POST de recorte da população, permitindo consulta via query parameters.")
    @ApiResponse(responseCode = "200", description = "Recorte gerado com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros ausentes ou inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha informada não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Conflito de versão ou mutação de dados", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Recorte incompleto", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/recorte-populacao")
    public ResponseEntity<RecortePopulacaoIaResponse> obterRecortePopulacaoPorGet(
            @RequestParam(required = false) Long campanhaId,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) Integer versaoCampanhaEsperada,
            @RequestParam(required = false) String hashRecorteEsperado) {
        RecortePopulacaoIaRequest request = new RecortePopulacaoIaRequest(
                campanhaId,
                null,
                null,
                competencia,
                dataInicio != null && !dataInicio.isBlank() ? LocalDate.parse(dataInicio) : null,
                dataFim != null && !dataFim.isBlank() ? LocalDate.parse(dataFim) : null,
                null,
                hashRecorteEsperado,
                versaoCampanhaEsperada
        );
        return ResponseEntity.ok(recorteService.obterRecorte(request));
    }

    @Operation(summary = "Verificar consistência do recorte sem retornar o payload completo",
            description = "Compara o hash canônico atual da população com o hash esperado da simulação para validar se a base sofreu mutação antes da consolidação do cálculo.")
    @ApiResponse(responseCode = "200", description = "Consistência verificada com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros ausentes ou inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Conflito de versão ou mutação de dados", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Recorte incompleto", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))

    @PostMapping("/recorte-populacao/verificar-consistencia")
    public ResponseEntity<ConsistenciaRecorteIaResponse> verificarConsistencia(@RequestBody VerificarConsistenciaRecorteIaRequest request) {
        return ResponseEntity.ok(recorteService.verificarConsistencia(request));
    }

    @Operation(summary = "Dados de vendas para simulação", description = "Vendas da competência, filtráveis por marca, loja, cargo (códigos) e matrícula, paginadas (máximo 1000 por página) e em ordem determinística (data, id). Página vazia = sem dados.")
    @ApiResponse(responseCode = "200", description = "Página de vendas")
    @ApiResponse(responseCode = "400", description = "Competência ausente ou inválida", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/dados-simulacao")
    public ResponseEntity<PaginaDTO<VendaSimulacaoDTO>> dadosSimulacao(
            @Parameter(description = "Competência YYYY-MM", example = "2026-09") @RequestParam(required = false) String competencia,
            @RequestParam(required = false) Integer marca,
            @RequestParam(required = false) Integer loja,
            @RequestParam(required = false) Integer cargo,
            @RequestParam(required = false) String matricula,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        return ResponseEntity.ok(service.buscarDadosSimulacao(competencia, marca, loja, cargo, matricula, page, size));
    }
}

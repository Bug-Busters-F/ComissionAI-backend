package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.regra.RegraRequest;
import com.bugbusters.backend.dto.regra.RegraResponse;
import com.bugbusters.backend.dto.regra.StatusRegra;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController 
@RequestMapping("/api/v1/regras")
@Tag(name = "1. Regras e campanhas", description = "Endpoints de gerenciamento manual do ciclo de vida das regras") 
public class RegraController {
    @Operation(summary = "Cadastrar nova regra", description = "Cria uma regra de comissão manual. Se dataFim for omitida, o sistema atribuirá 30 dias a partir da dataInicio.")
    @ApiResponse(responseCode = "201", description = "Regra criada com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping 
    public ResponseEntity<RegraResponse> criarRegra(@Valid @RequestBody RegraRequest request) {
        LocalDate fimCalculado = request.dataFim() != null ? request.dataFim() : request.dataInicio().plusDays(30);
        RegraResponse response = new RegraResponse(
            1L, request.nome(), request.canal(), request.codMarca(), request.descrMarca(), request.codLoja(),
            request.codCargo(), request.descriCargo(), request.matricula(),
            request.taxa(), request.dataInicio(), fimCalculado, StatusRegra.ATIVA, OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Listar regras cadastradas", description = "Retorna lista de regras com filtro opcional por canal e status")
    @GetMapping 
    public ResponseEntity<List<RegraResponse>> listarRegras(
            @RequestParam(required = false) String canal,
            @RequestParam(required = false) StatusRegra status) {
        return ResponseEntity.ok(List.of(
                new RegraResponse(1L, "Regra Padrão E-commerce", "ECOMMERCE", new BigDecimal("0.0500"),
                    LocalDate.now(), LocalDate.now().plusDays(30), StatusRegra.ATIVA, OffsetDateTime.now())
        ));
    }

    @Operation(summary = "Consultar regra por ID")
    @GetMapping("/{id}")
    public ResponseEntity<RegraResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(new RegraResponse(id, "Regra Local", "LOJA_FISICA", new BigDecimal("0.0800"),
            LocalDate.now(), LocalDate.now().plusDays(30), StatusRegra.ATIVA, OffsetDateTime.now()));
    }

    @Operation(summary = "Desativar regra")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativarRegra(@PathVariable Long id) {
        return ResponseEntity.noContent().build();
    }
}
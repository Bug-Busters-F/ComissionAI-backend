package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.canal.CanalResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/canais")
@Tag(name = "Canais de Venda", description = "Consulta de canais de venda suportados pelas regras de comissionamento")
public class CanalController {

    private static final List<CanalResponseDTO> CANAIS = List.of(
            new CanalResponseDTO("ECOMMERCE", "E-Commerce", "Vendas realizadas através do canal digital e loja virtual", false),
            new CanalResponseDTO("LOJA_FISICA", "Loja Física", "Vendas presenciais realizadas em ponto de venda / loja física", true),
            new CanalResponseDTO("WHATSAPP", "WhatsApp", "Vendas remotas assistidas por mensageria e WhatsApp", false),
            new CanalResponseDTO("PARCEIRO", "Parceiros / Afiliados", "Vendas originadas de parcerias comerciais e afiliados", false)
    );

    @Operation(summary = "Listar canais de venda", description = "Retorna todos os canais de venda suportados pelo motor de regras.")
    @ApiResponse(responseCode = "200", description = "Canais recuperados com sucesso")
    @GetMapping
    public List<CanalResponseDTO> listarCanais() {
        return CANAIS;
    }
}

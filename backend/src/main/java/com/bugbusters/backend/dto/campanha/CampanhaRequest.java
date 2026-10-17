package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.EstadoCampanha;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Dados para cadastro ou edição de campanha com coleção de regras vinculadas")
public record CampanhaRequest(
        @Schema(description = "Título descritivo da campanha", example = "Campanha Black Friday 2026")
        @NotBlank(message = "O título da campanha é obrigatório")
        String titulo,

        @Schema(description = "Texto original em linguagem natural que originou as propostas", example = "1. Pagar 5% no ecommerce em outubro. 2. Loja 75 pagar taxa base + 1.5%")
        @NotBlank(message = "O texto original da campanha é obrigatório")
        String textoOriginal,

        @Schema(description = "Data de início da vigência da campanha. Se omitida, assume a data atual.", example = "2026-10-01")
        LocalDate dataInicio,

        @Schema(description = "Data final da vigência da campanha. Se omitida, aplica-se automaticamente 30 dias contados da data de início.", example = "2026-10-31")
        LocalDate dataFim,

        @Schema(description = "Estado desejado para a campanha (DRAFT por padrão na criação)", example = "DRAFT")
        EstadoCampanha estado,

        @Schema(description = "Coleção de regras vinculadas à campanha")
        @NotEmpty(message = "A campanha deve conter pelo menos uma regra vinculada")
        List<@Valid RegraItemRequest> regras
) {
    public CampanhaRequest(String titulo, String textoOriginal, LocalDate dataInicio, LocalDate dataFim, List<RegraItemRequest> regras) {
        this(titulo, textoOriginal, dataInicio, dataFim, EstadoCampanha.DRAFT, regras);
    }
}
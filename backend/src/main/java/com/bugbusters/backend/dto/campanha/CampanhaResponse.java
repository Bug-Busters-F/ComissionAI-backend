package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.dto.regra.StatusRegra;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "Representação detalhada da campanha e da regra vinculada")
public record CampanhaResponse(
        Long id,
        String titulo,
        String textoOriginal,
        String estado,
        LocalDate dataInicio,
        LocalDate dataFim,
        RegraVinculadaDTO regra,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
    public record RegraVinculadaDTO(
            Long id,
            String nome,
            String canal,
            Integer codMarca,
            String descrMarca,
            Integer codLoja,
            Integer codCargo,
            String descriCargo,
            String matricula,
            BigDecimal taxa,
            LocalDate dataInicio,
            LocalDate dataFim,
            StatusRegra status
    ) {
        public RegraVinculadaDTO(Long id, String nome, String canal, BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim, StatusRegra status) {
            this(id, nome, canal, null, null, null, null, null, null, taxa, dataInicio, dataFim, status);
        }

        public RegraVinculadaDTO(Long id, String nome, String canal, Integer codMarca, Integer codLoja,
                                 Integer codCargo, String descriCargo, String matricula,
                                 BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim, StatusRegra status) {
            this(id, nome, canal, codMarca, null, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim, status);
        }
    }
}
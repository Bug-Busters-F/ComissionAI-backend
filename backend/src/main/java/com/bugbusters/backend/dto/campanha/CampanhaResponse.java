package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.EstadoCampanha;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Representação detalhada da campanha e da regra vinculada")
public record CampanhaResponse(
        Long id,
        String titulo,
        String textoOriginal,
        EstadoCampanha estado,
        LocalDate dataInicio,
        LocalDate dataFim,
        @Schema(description = "Primeira regra da campanha (compatibilidade com o contrato de uma regra)")
        RegraVinculadaDTO regra,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        @Schema(description = "Todas as regras ativas (não removidas) da campanha, em ordem de criação")
        List<RegraVinculadaDTO> regras
) {
    public CampanhaResponse(Long id, String titulo, String textoOriginal, EstadoCampanha estado, LocalDate dataInicio,
                            LocalDate dataFim, RegraVinculadaDTO regra, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
        this(id, titulo, textoOriginal, estado, dataInicio, dataFim, regra, criadoEm, atualizadoEm,
                regra != null ? List.of(regra) : List.of());
    }

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
            StatusRegra status,
            BigDecimal valorMin,
            BigDecimal valorMax,
            Boolean minInclusivo,
            Boolean maxInclusivo,
            BigDecimal bonusPercentual,
            BigDecimal bonusValorFixo
    ) {
        public RegraVinculadaDTO(Long id, String nome, String canal, Integer codMarca, String descrMarca, Integer codLoja,
                                 Integer codCargo, String descriCargo, String matricula, BigDecimal taxa,
                                 LocalDate dataInicio, LocalDate dataFim, StatusRegra status) {
            this(id, nome, canal, codMarca, descrMarca, codLoja, codCargo, descriCargo, matricula, taxa, dataInicio, dataFim,
                    status, null, null, null, null, null, null);
        }

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
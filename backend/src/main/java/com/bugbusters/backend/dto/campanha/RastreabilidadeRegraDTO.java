package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.dto.interpretador.proposta.*;
import java.util.List;
import java.util.Map;

/** Evidências da proposta atual; ausência significa origem desconhecida, nunca inferida. */
public record RastreabilidadeRegraDTO(
        Map<String, OrigemCampo> origensPorCampo,
        List<ReferenciaConsultadaDTO> referenciasConsultadas,
        List<String> camposManuais,
        List<PendenciaRastreabilidadeDTO> pendencias,
        List<PendenciaRastreabilidadeDTO> pendenciasCampanha,
        Boolean publicoTodasVendas,
        Boolean artefatosAtualizados) {
    public RastreabilidadeRegraDTO {
        origensPorCampo = origensPorCampo == null ? Map.of() : Map.copyOf(origensPorCampo);
        referenciasConsultadas = referenciasConsultadas == null ? List.of() : List.copyOf(referenciasConsultadas);
        camposManuais = camposManuais == null ? List.of() : List.copyOf(camposManuais);
        pendenciasCampanha = pendenciasCampanha == null ? List.of() : List.copyOf(pendenciasCampanha);
        pendencias = pendencias == null ? List.of() : List.copyOf(pendencias);
    }
}

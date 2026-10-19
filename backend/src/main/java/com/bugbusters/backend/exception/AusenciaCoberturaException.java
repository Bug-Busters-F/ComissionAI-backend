package com.bugbusters.backend.exception;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import com.bugbusters.backend.dto.basecomiss.ReferenciaFaltanteDTO;

public class AusenciaCoberturaException extends BusinessException {

    private final YearMonth competencia;
    private final List<ReferenciaFaltanteDTO> referenciasFaltantes;

    public AusenciaCoberturaException(YearMonth competencia, List<ReferenciaFaltanteDTO> referenciasFaltantes) {
        super(montarMensagem(competencia, referenciasFaltantes));
        this.competencia = competencia;
        this.referenciasFaltantes = referenciasFaltantes != null ? List.copyOf(referenciasFaltantes) : List.of();
    }

    public YearMonth getCompetencia() {
        return competencia;
    }

    public List<ReferenciaFaltanteDTO> getReferenciasFaltantes() {
        return referenciasFaltantes;
    }

    private static String montarMensagem(YearMonth ym, List<ReferenciaFaltanteDTO> faltantes) {
        String compStr = ym != null ? ym.toString() : "não informada";
        String lista = (faltantes != null && !faltantes.isEmpty())
                ? faltantes.stream()
                        .map(f -> String.format("[Marca: %s (%s) - Cargo: %s (%s)]",
                                f.codMarca() != null ? f.codMarca() : "-",
                                f.descrMarca() != null ? f.descrMarca() : "-",
                                f.codCargo() != null ? f.codCargo() : "-",
                                f.descriCargo() != null ? f.descriCargo() : "-"))
                        .collect(Collectors.joining(", "))
                : "nenhuma referência informada";
        return String.format("Processamento impedido por ausência de cobertura de taxa base (tb_basecomiss) para a competência %s. Referências faltantes: %s.",
                compStr, lista);
    }
}

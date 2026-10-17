package com.bugbusters.backend.dto.interpretador;

import com.bugbusters.backend.model.Cargo;
import com.bugbusters.backend.model.CatalogoDominioDomRock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO para exposição das dimensões e informações de domínio do cliente para Frontend e IA.
 */
public record CatalogoDominioResponseDTO(
        Map<Integer, String> marcas,
        Map<Integer, String> cargos,
        List<CargoDetalhadoDTO> cargosDetalhados,
        List<String> canais,
        Map<Integer, String> lojas,
        String faixaMatriculas,
        List<String> competenciasDocumentadas,
        List<TaxaContratualDTO> taxasContratuaisBase
) {

    public record CargoDetalhadoDTO(
            Integer codigo,
            String descricao,
            String canalPadrao
    ) {}

    public record TaxaContratualDTO(
            Integer codMarca,
            String descrMarca,
            Integer codCargo,
            String descriCargo,
            BigDecimal percentual
    ) {}

    public static CatalogoDominioResponseDTO construir() {
        List<CargoDetalhadoDTO> cargosDetalhados = new ArrayList<>();
        for (Cargo c : Cargo.values()) {
            cargosDetalhados.add(new CargoDetalhadoDTO(c.getCodigo(), c.getDescricao(), c.getCanalPadrao()));
        }

        List<TaxaContratualDTO> taxas = new ArrayList<>();
        for (Map.Entry<Integer, String> marcaEntry : CatalogoDominioDomRock.MARCAS.entrySet()) {
            Integer codMarca = marcaEntry.getKey();
            String descrMarca = marcaEntry.getValue();

            for (Cargo cargo : Cargo.values()) {
                CatalogoDominioDomRock.obterTaxaBaseContratual(codMarca, cargo.getDescricao())
                        .ifPresent(taxa -> taxas.add(new TaxaContratualDTO(
                                codMarca,
                                descrMarca,
                                cargo.getCodigo(),
                                cargo.getDescricao(),
                                taxa
                        )));
            }
        }

        return new CatalogoDominioResponseDTO(
                CatalogoDominioDomRock.MARCAS,
                CatalogoDominioDomRock.CARGOS_POR_CODIGO,
                cargosDetalhados,
                CatalogoDominioDomRock.CANAIS,
                CatalogoDominioDomRock.LOJAS,
                "MATRIC-1 a MATRIC-600",
                CatalogoDominioDomRock.COMPETENCIAS_HISTORICAS,
                taxas
        );
    }
}

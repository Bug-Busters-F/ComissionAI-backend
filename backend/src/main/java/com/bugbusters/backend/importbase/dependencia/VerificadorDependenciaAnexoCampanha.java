package com.bugbusters.backend.importbase.dependencia;

import com.bugbusters.backend.model.CampanhaAnexoBase;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.repository.CampanhaAnexoBaseRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Impede a exclusão de lotes de importação que estejam atualmente anexados a campanhas ativas (removidoEm IS NULL).
 */
@Component
public class VerificadorDependenciaAnexoCampanha implements VerificadorDependenciaLote {

    private final CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;

    public VerificadorDependenciaAnexoCampanha(CampanhaAnexoBaseRepository campanhaAnexoBaseRepository) {
        this.campanhaAnexoBaseRepository = campanhaAnexoBaseRepository;
    }

    @Override
    public List<DependenciaMotivoDTO> verificar(EnvioArquivo lote) {
        if (lote == null || lote.getId() == null) {
            return List.of();
        }

        List<CampanhaAnexoBase> anexos = campanhaAnexoBaseRepository.findAnexosAtivosDeCampanhasNaoRemovidas(lote.getId());
        if (anexos.isEmpty()) {
            return List.of();
        }

        List<DependenciaMotivoDTO> motivos = new ArrayList<>();
        String nomesCampanhas = anexos.stream()
                .map(a -> String.format("%s (ID: %d)", a.getCampanha().getTitulo(), a.getCampanha().getId()))
                .distinct()
                .collect(Collectors.joining(", "));

        motivos.add(new DependenciaMotivoDTO(
                "LOTE_ANEXADO_CAMPANHA",
                String.format("O lote está anexado a %d campanha(s) ativa(s): %s. Desanexe o lote da campanha antes de excluí-lo.",
                        anexos.size(), nomesCampanhas)
        ));

        return motivos;
    }
}

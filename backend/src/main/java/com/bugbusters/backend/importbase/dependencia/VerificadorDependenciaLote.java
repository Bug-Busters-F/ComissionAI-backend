package com.bugbusters.backend.importbase.dependencia;

import com.bugbusters.backend.model.EnvioArquivo;
import java.util.List;

public interface VerificadorDependenciaLote {
    List<DependenciaMotivoDTO> verificar(EnvioArquivo lote);
}

package com.bugbusters.backend.auth;

import java.util.List;

public final class PerfilPermissoes {
    private PerfilPermissoes() { }

    public static List<String> permissoes(PapelUsuario papel) {
        return switch (papel) {
            case ADMINISTRADOR -> List.of("ACESSO_COMPLETO", "GERENCIAR_USUARIOS", "CONSULTAR_TODOS_LOGS");
            case ANALISTA_NEGOCIOS -> List.of("EDITAR_CAMPANHAS", "SIMULAR", "REVISAR_PROPOSTAS");
            case GERENTE_VENDAS -> List.of("PUBLICAR_CAMPANHAS", "IMPORTAR_BASES", "CALCULAR_COMPETENCIA", "CONSULTAR_LOGS_ANALISTAS");
            case ANALISTA_CODIGO -> List.of("REVISAR_CODIGO", "APROVAR_CODIGO", "DEVOLVER_CODIGO");
            case AUDITOR -> List.of("CONSULTAR_DADOS", "CONSULTAR_LOGS_AUTORIZADOS");
        };
    }
}

package com.bugbusters.backend.auth;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;

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

    /**
     * Regra centralizada de visibilidade de logs no servidor (BACK-17 / BACK-1):
     * ADMINISTRADOR vê tudo (null = sem filtro restritivo);
     * GERENTE_VENDAS vê apenas ações dos dois tipos de analistas;
     * AUDITOR vê ações de ambos os analistas e de gerente;
     * demais perfis têm leitura negada.
     */
    public static List<String> perfisLogsVisiveis(PapelUsuario papel) {
        if (papel == null) {
            throw new AccessDeniedException("Seu perfil não tem permissão para consultar logs.");
        }
        return switch (papel) {
            case ADMINISTRADOR -> null;
            case GERENTE_VENDAS -> List.of(
                    PapelUsuario.ANALISTA_NEGOCIOS.name(),
                    PapelUsuario.ANALISTA_CODIGO.name()
            );
            case AUDITOR -> List.of(
                    PapelUsuario.ANALISTA_NEGOCIOS.name(),
                    PapelUsuario.ANALISTA_CODIGO.name(),
                    PapelUsuario.GERENTE_VENDAS.name()
            );
            case ANALISTA_NEGOCIOS, ANALISTA_CODIGO -> throw new AccessDeniedException(
                    "Seu perfil não tem permissão para consultar logs do sistema.");
        };
    }
}

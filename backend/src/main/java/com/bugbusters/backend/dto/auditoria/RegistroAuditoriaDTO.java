package com.bugbusters.backend.dto.auditoria;

import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dados para registro manual ou programático de auditoria")
public record RegistroAuditoriaDTO(
        @Schema(description = "Identificação do usuário ou agente responsável", example = "gestor.campanhas@empresa.com")
        @NotBlank(message = "O usuário responsável é obrigatório.")
        String usuario,

        @Schema(description = "Perfil ou papel do usuário", example = "GESTOR_CAMPANHA")
        String perfilUsuario,

        @Schema(description = "Tipo da ação auditável", example = "APROVACAO_PROGRAMA_PYTHON")
        @NotNull(message = "O tipo da ação é obrigatório.")
        TipoAcaoAuditoria tipoAcao,

        @Schema(description = "Tipo do recurso/entidade auditada", example = "CAMPANHA")
        @NotNull(message = "O tipo de entidade é obrigatório.")
        TipoEntidadeAuditoria tipoEntidade,

        @Schema(description = "Identificador do recurso/entidade", example = "42")
        String entidadeId,

        @Schema(description = "Versão do recurso (se aplicável)", example = "2")
        Integer versaoEntidade,

        @Schema(description = "Descrição humana detalhada da ação realizada", example = "Aprovação técnica do script Python gerado para a campanha")
        @NotBlank(message = "A descrição da ação é obrigatória.")
        String descricao,

        @Schema(description = "Snapshot ou estado anterior (JSON serializado)", example = "{\"status\": \"PENDENTE\"}")
        String dadosAnteriores,

        @Schema(description = "Snapshot ou estado posterior / parâmetros aplicados (JSON serializado)", example = "{\"status\": \"APROVADO\"}")
        String dadosNovos,

        @Schema(description = "IP de origem da solicitação", example = "192.168.1.10")
        String ipOrigem,

        @Schema(description = "User agent do cliente", example = "Mozilla/5.0")
        String userAgent,

        @Schema(description = "Indica se a operação foi bem-sucedida", example = "true")
        Boolean sucesso,

        @Schema(description = "Detalhe do erro ocorrido caso a operação tenha falhado", example = "Assinatura inválida")
        String detalheErro
) {
    public RegistroAuditoriaDTO {
        if (usuario == null || usuario.isBlank()) {
            usuario = "SISTEMA";
        }
        if (sucesso == null) {
            sucesso = true;
        }
    }
}


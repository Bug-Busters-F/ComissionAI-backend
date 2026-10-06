package com.bugbusters.backend.dto.interpretador.proposta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Origem de inferência e rastreabilidade para auditoria de cada campo da proposta")
public enum OrigemCampo {
    @Schema(description = "Extraído diretamente do texto em linguagem natural informado pelo usuário")
    TEXTO,

    @Schema(description = "Inferido a partir do contexto fornecido na requisição (ex: ano de referência, canal padrão)")
    CONTEXTO,

    @Schema(description = "Consultado a partir dos cadastros persistidos no banco de dados (ex: tb_basecomiss, tb_brand, tb_position)")
    BASE_DADOS,

    @Schema(description = "Atribuído por regra ou valor padrão do sistema quando omitido na instrução")
    PADRAO_SISTEMA
}

package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.dto.interpretador.proposta.PendenciaPropostaDTO;
import com.bugbusters.backend.dto.interpretador.proposta.PropostaRegraDTO;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Marca;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Item de regra pertencente a uma coleção vinculada à campanha")
public record RegraItemRequest(
        @Schema(description = "Identificador do bloco ou proposta", example = "bloco-1")
        String blocoId,

        @Schema(description = "Trecho do texto original em linguagem natural", example = "Comissão de 5% no canal ecommerce")
        String trechoOrigem,

        @Schema(description = "Nome descritivo da regra", example = "Regra E-commerce Geral")
        String nome,

        @Schema(description = "Canal de venda (ex: ECOMMERCE, LOJA_FISICA)", example = "ECOMMERCE")
        String canal,

        @Schema(description = "Código numérico da marca", example = "10")
        Integer codMarca,

        @Schema(description = "Descrição textual / cor da empresa confidencial", example = "PRETO")
        String descrMarca,

        @Schema(description = "Código numérico da loja (1 a 80)", example = "75")
        Integer codLoja,

        @Schema(description = "Código numérico do cargo", example = "100")
        Integer codCargo,

        @Schema(description = "Descrição textual do cargo", example = "VENDEDOR LOJA")
        String descriCargo,

        @Schema(description = "Matrícula individual do vendedor (MATRIC-1 a MATRIC-600)", example = "MATRIC-56")
        String matricula,

        @Schema(description = "Valor mínimo de venda para aplicação da regra", example = "5000.00")
        BigDecimal valorMinimo,

        @Schema(description = "Indica se o valor mínimo é inclusivo (>=) ou exclusivo (>)", example = "false")
        Boolean minInclusivo,

        @Schema(description = "Valor máximo de venda para aplicação da regra", example = "20000.00")
        BigDecimal valorMaximo,

        @Schema(description = "Indica se o valor máximo é inclusivo (<=) ou exclusivo (<)", example = "true")
        Boolean maxInclusivo,

        @Schema(description = "Tipo de operação sobre a base ou definição direta", example = "DEFINIR_TAXA")
        TipoOperacaoBase tipoOperacao,

        @Schema(description = "Valor do ajuste sobre a base ou taxa direta", example = "0.0500")
        BigDecimal valorAjuste,

        @Schema(description = "Tipo de base de comissão consultada", example = "BASE_COMISS")
        String tipoBaseReferencia,

        @Schema(description = "Taxa contratual base consultada", example = "0.0300")
        BigDecimal taxaBaseConsultada,

        @Schema(description = "Descrição da referência contratual consultada", example = "Taxa Contratual Padrão")
        String descricaoReferencia,

        @Schema(description = "Taxa percentual efetiva final da regra (ex: 0.0500 = 5%)", example = "0.0500")
        BigDecimal taxa,

        @Schema(description = "Artefato explicativo em linguagem natural (XAI)", example = "Aplica taxa de 5.00% para vendas no canal ECOMMERCE.")
        String explicacao,

        @Schema(description = "Artefato de código determinístico em Python equivalente", example = "def calcular_comissao(venda): ...")
        String pythonEquivalente,

        @Schema(description = "Lista de mensagens de pendências ou avisos apontados na elaboração")
        List<String> pendencias,

        @Schema(description = "Indica se a regra possui todos os campos válidos ou se é proposta incompleta", example = "true")
        Boolean completa,

        @Schema(description = "Status individual desejado para a regra (opcional)", example = "DRAFT")
        StatusRegra status
) {
    public RegraItemRequest {
        if (descrMarca != null && !descrMarca.isBlank()) {
            descrMarca = Marca.padronizar(descrMarca);
            if (codMarca == null) {
                codMarca = Marca.buscarPorNome(descrMarca).map(Marca::getCodigo).orElse(null);
            }
        } else if (codMarca != null && descrMarca == null) {
            descrMarca = Marca.buscarPorCodigo(codMarca).map(Marca::getDescricao).orElse(null);
        }
        if (canal != null) {
            canal = canal.trim().toUpperCase();
        }
        if (tipoOperacao == null) {
            tipoOperacao = TipoOperacaoBase.DEFINIR_TAXA;
        }
        if (completa == null) {
            completa = (taxa != null);
        }
    }

    public static RegraItemRequest deProposta(PropostaRegraDTO p) {
        if (p == null) return null;
        return new RegraItemRequest(
                p.blocoId(),
                p.trechoOrigem(),
                p.blocoId() != null ? "Regra " + p.blocoId() : "Regra Proposta",
                p.filtros() != null ? p.filtros().canal() : null,
                p.filtros() != null ? p.filtros().codMarca() : null,
                p.filtros() != null ? p.filtros().descrMarca() : null,
                p.filtros() != null ? p.filtros().codLoja() : null,
                p.filtros() != null ? p.filtros().codCargo() : null,
                p.filtros() != null ? p.filtros().descriCargo() : null,
                p.filtros() != null ? p.filtros().matricula() : null,
                p.condicaoValor() != null ? p.condicaoValor().valorMinimo() : null,
                p.condicaoValor() != null ? p.condicaoValor().minInclusivo() : null,
                p.condicaoValor() != null ? p.condicaoValor().valorMaximo() : null,
                p.condicaoValor() != null ? p.condicaoValor().maxInclusivo() : null,
                p.operacaoBase() != null ? p.operacaoBase().tipoOperacao() : TipoOperacaoBase.DEFINIR_TAXA,
                p.operacaoBase() != null ? p.operacaoBase().valorAjuste() : null,
                p.operacaoBase() != null && p.operacaoBase().referenciaBase() != null ? p.operacaoBase().referenciaBase().tipoBase() : null,
                p.operacaoBase() != null && p.operacaoBase().referenciaBase() != null ? p.operacaoBase().referenciaBase().taxaBaseConsultada() : null,
                p.operacaoBase() != null && p.operacaoBase().referenciaBase() != null ? p.operacaoBase().referenciaBase().descricao() : null,
                p.taxaFinal(),
                p.explicacao(),
                p.pythonEquivalente(),
                p.pendencias() != null ? p.pendencias().stream().map(PendenciaPropostaDTO::mensagem).toList() : List.of(),
                p.completa(),
                StatusRegra.DRAFT
        );
    }
}

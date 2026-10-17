package com.bugbusters.backend.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Representa os cargos e funções oficiais mapeados na base de RH e tabela de comissões do cliente.
 * O cliente possui 5 cargos formais distribuídos em 4 códigos de cargo:
 *      100: VENDEDOR LOJA (Canal LOJA_FISICA)
 *      150: GERENTE DE LOJA (Canal LOJA_FISICA)
 *      150: GERENTE QUIOSQUE (Canal QUIOSQUE)
 *      200: VENDEDOR BALCAO (Canal BALCAO)
 *      300: ASSISTENTE DE VENDAS (Canal LOJA_FISICA)
 */
public enum Cargo {
    VENDEDOR_LOJA(100, "VENDEDOR LOJA", "LOJA_FISICA"),
    GERENTE_DE_LOJA(150, "GERENTE DE LOJA", "LOJA_FISICA"),
    GERENTE_QUIOSQUE(150, "GERENTE QUIOSQUE", "QUIOSQUE"),
    VENDEDOR_BALCAO(200, "VENDEDOR BALCAO", "BALCAO"),
    ASSISTENTE_DE_VENDAS(300, "ASSISTENTE DE VENDAS", "LOJA_FISICA");

    private final int codigo;
    private final String descricao;
    private final String canalPadrao;

    Cargo(int codigo, String descricao, String canalPadrao) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.canalPadrao = canalPadrao;
    }

    public int getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCanalPadrao() {
        return canalPadrao;
    }
    
    // Remove acentos e normaliza para caixa alta sem espaços sobressalentes.
    public static String padronizar(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String nfd = Normalizer.normalize(input.trim().toUpperCase(), Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "");
    }

    /**
     * Localiza o cargo exato pelo nome/descrição normalizado.
     */
    public static Optional<Cargo> buscarPorDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return Optional.empty();
        }
        String padrao = padronizar(descricao);

        for (Cargo c : values()) {
            if (padronizar(c.descricao).equals(padrao) || c.name().equalsIgnoreCase(padrao)) {
                return Optional.of(c);
            }
        }

        // Reconhecimento flexível por palavras-chave
        if (padrao.contains("QUIOSQUE")) {
            return Optional.of(GERENTE_QUIOSQUE);
        }
        if (padrao.contains("BALCAO")) {
            return Optional.of(VENDEDOR_BALCAO);
        }
        if (padrao.contains("ASSISTENTE")) {
            return Optional.of(ASSISTENTE_DE_VENDAS);
        }
        if (padrao.contains("GERENTE") && padrao.contains("LOJA")) {
            return Optional.of(GERENTE_DE_LOJA);
        }
        if (padrao.contains("VENDEDOR") && padrao.contains("LOJA")) {
            return Optional.of(VENDEDOR_LOJA);
        }

        return Optional.empty();
    }

    /**
     * Retorna os cargos associados a um código numérico.
     * O código 150 retorna tanto GERENTE_DE_LOJA quanto GERENTE_QUIOSQUE.
     */
    public static List<Cargo> buscarPorCodigo(Integer codigo) {
        if (codigo == null) {
            return List.of();
        }
        return Arrays.stream(values())
                .filter(c -> c.codigo == codigo)
                .toList();
    }
}

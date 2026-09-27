package com.bugbusters.backend.model;

import java.util.Arrays;
import java.util.Optional;

public enum Marca {
    PRETO(10, "PRETO"),
    BRANCO(20, "BRANCO"),
    AZUL(30, "AZUL"),
    VERMELHO(40, "VERMELHO"),
    AMARELO(50, "AMARELO"),
    CINZA(60, "CINZA");

    private final int codigo;
    private final String descricao;

    Marca(int codigo, String descricao) {
        this.codigo = codigo;
        this.descricao = descricao;
    }

    public int getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Formata e padroniza o texto do nome da empresa/marca (cor confidencial).
     * Resolve o problema de case sensitivity (ex: "Vermelho", "vermelho", "  veRmelho  " -> "VERMELHO").
     * Permite cores existentes e novas cores (não bloqueia novas empresas).
     *
     * @param input texto recebido
     * @return texto formatado e padronizado em maiúsculo sem espaços nas extremidades, ou null se nulo/vazio
     */
    public static String padronizar(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim().toUpperCase();
    }

    public static Optional<Marca> buscarPorCodigo(Integer codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(m -> m.codigo == codigo)
                .findFirst();
    }

    public static Optional<Marca> buscarPorNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return Optional.empty();
        }
        String formatado = padronizar(nome);
        return Arrays.stream(values())
                .filter(m -> m.name().equals(formatado) || m.descricao.equalsIgnoreCase(formatado))
                .findFirst();
    }
}

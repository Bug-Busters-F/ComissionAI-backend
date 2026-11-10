package com.bugbusters.backend.auth;

public enum PapelUsuario {
    ADMINISTRADOR("Administrador"), ANALISTA_NEGOCIOS("Analista de negócios"),
    GERENTE_VENDAS("Gerente de vendas"), ANALISTA_CODIGO("Analista de código"), AUDITOR("Auditor");
    private final String nome;
    PapelUsuario(String nome) { this.nome = nome; }
    public String getNome() { return nome; }
}

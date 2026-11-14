package com.bugbusters.backend.auth;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_usuario")
public class Usuario {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelUsuario papel;
    @Column(nullable = false)
    private boolean ativo = true;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    void criar() {
        if (id == null)
            id = UUID.randomUUID();
        criadoEm = atualizadoEm = OffsetDateTime.now();
    }

    @PreUpdate
    void atualizar() {
        atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String v) {
        nome = v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        email = v.toLowerCase();
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String v) {
        senhaHash = v;
    }

    public PapelUsuario getPapel() {
        return papel;
    }

    public void setPapel(PapelUsuario v) {
        papel = v;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean v) {
        ativo = v;
    }
}

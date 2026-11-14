package com.bugbusters.backend.auth;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_sessao_usuario")
public class SessaoUsuario {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm;
    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;
    @Column(name = "encerrada_em")
    private OffsetDateTime encerradaEm;

    @PrePersist
    void criar() {
        if (id == null)
            id = UUID.randomUUID();
        if (criadaEm == null)
            criadaEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario u) {
        usuario = u;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String v) {
        tokenHash = v;
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(OffsetDateTime v) {
        expiraEm = v;
    }

    public OffsetDateTime getEncerradaEm() {
        return encerradaEm;
    }

    public void encerrar() {
        encerradaEm = OffsetDateTime.now();
    }
}

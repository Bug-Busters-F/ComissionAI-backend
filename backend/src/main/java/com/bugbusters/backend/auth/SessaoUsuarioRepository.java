package com.bugbusters.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SessaoUsuarioRepository extends JpaRepository<SessaoUsuario, UUID> {
    Optional<SessaoUsuario> findByTokenHash(String tokenHash);

    List<SessaoUsuario> findByUsuarioIdAndEncerradaEmIsNull(UUID usuarioId);
}

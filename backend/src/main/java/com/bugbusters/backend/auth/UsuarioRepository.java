package com.bugbusters.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
    long countByPapelAndAtivoTrue(PapelUsuario papel);

    // Serializa remoções de privilégios, inclusive quando dois administradores agem juntos.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.papel = com.bugbusters.backend.auth.PapelUsuario.ADMINISTRADOR order by u.id")
    List<Usuario> bloquearAdministradores();
}

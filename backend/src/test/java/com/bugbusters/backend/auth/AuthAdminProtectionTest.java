package com.bugbusters.backend.auth;

import com.bugbusters.backend.exception.ResourceConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthAdminProtectionTest {
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final SessaoUsuarioRepository sessoes = mock(SessaoUsuarioRepository.class);
    final AuthService service = new AuthService(usuarios, sessoes, mock(PasswordEncoder.class), 8);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    Usuario admin() {
        Usuario u = new Usuario(); u.criar(); u.setPapel(PapelUsuario.ADMINISTRADOR);
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
        when(usuarios.countByPapelAndAtivoTrue(PapelUsuario.ADMINISTRADOR)).thenReturn(1L);
        return u;
    }
    @Test void ultimoAdminNaoPodeSerDesativado() {
        Usuario u = admin();
        assertThrows(ResourceConflictException.class, () -> service.alterarAtivacao(u.getId(), false));
        assertTrue(u.isAtivo());
        verify(usuarios).bloquearAdministradores();
        verifyNoInteractions(sessoes);
    }
    @Test void ultimoAdminNaoPodeSerRebaixado() {
        Usuario u = admin();
        assertThrows(ResourceConflictException.class, () -> service.alterarPerfil(u.getId(), PapelUsuario.AUDITOR));
        assertEquals(PapelUsuario.ADMINISTRADOR, u.getPapel());
        verify(usuarios).bloquearAdministradores();
    }
    @Test void outroAdminPodeSerRebaixadoQuandoRestaAdministradorAtivo() {
        Usuario u = admin();
        when(usuarios.countByPapelAndAtivoTrue(PapelUsuario.ADMINISTRADOR)).thenReturn(2L);
        service.alterarPerfil(u.getId(), PapelUsuario.AUDITOR);
        assertEquals(PapelUsuario.AUDITOR, u.getPapel());
    }
}

package com.bugbusters.backend.auth;

import com.bugbusters.backend.auth.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService s) {
        service = s;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest r) {
        return ResponseEntity.ok(service.login(r));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String h) {
        service.logout(h.substring(7));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/me")
    public UsuarioResponse me(@AuthenticationPrincipal Usuario u) {
        return UsuarioResponse.de(u);
    }

    @PatchMapping("/auth/me")
    public UsuarioResponse atualizarMeuPerfil(@AuthenticationPrincipal Usuario u,
            @Valid @RequestBody AtualizarUsuarioRequest request) {
        return service.atualizarMeuPerfil(u, request);
    }

    @PatchMapping("/auth/me/senha")
    public ResponseEntity<Void> alterarMinhaSenha(@AuthenticationPrincipal Usuario u,
            @Valid @RequestBody AlterarSenhaRequest request) {
        service.alterarMinhaSenha(u, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/perfis")
    public java.util.List<PerfilResponse> perfis() { return service.perfis(); }

    @GetMapping("/usuarios")
    public java.util.List<UsuarioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/usuarios/{id}")
    public UsuarioResponse buscar(@PathVariable java.util.UUID id) { return service.buscar(id); }

    @PostMapping("/usuarios")
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(r));
    }

    @PutMapping("/usuarios/{id}")
    public UsuarioResponse atualizar(@PathVariable java.util.UUID id, @Valid @RequestBody AtualizarUsuarioRequest request) {
        return service.atualizar(id, request);
    }

    @PatchMapping("/usuarios/{id}/perfil")
    public UsuarioResponse alterarPerfil(@PathVariable java.util.UUID id, @Valid @RequestBody AlterarPerfilRequest request) {
        return service.alterarPerfil(id, request.papel());
    }

    @PatchMapping("/usuarios/{id}/ativo")
    public UsuarioResponse ativar(@PathVariable java.util.UUID id, @RequestParam boolean ativo) {
        return service.alterarAtivacao(id, ativo);
    }

    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<Void> excluirLogicamente(@PathVariable java.util.UUID id) {
        service.excluirLogicamente(id);
        return ResponseEntity.noContent().build();
    }
}

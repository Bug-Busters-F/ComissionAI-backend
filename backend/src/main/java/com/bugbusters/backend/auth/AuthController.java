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

    @GetMapping("/usuarios")
    public java.util.List<UsuarioResponse> listar() {
        return service.listar();
    }

    @PostMapping("/usuarios")
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(r));
    }

    @PatchMapping("/usuarios/{id}/ativo")
    public UsuarioResponse ativar(@PathVariable java.util.UUID id, @RequestParam boolean ativo) {
        return service.alterarAtivacao(id, ativo);
    }
}

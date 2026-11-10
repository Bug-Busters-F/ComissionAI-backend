package com.bugbusters.backend.auth;

import com.bugbusters.backend.auth.dto.*;
import com.bugbusters.backend.exception.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UsuarioRepository usuarios;
    private final SessaoUsuarioRepository sessoes;
    private final PasswordEncoder encoder;
    private final Duration duracao;

    public AuthService(UsuarioRepository u, SessaoUsuarioRepository s, PasswordEncoder e,
            @Value("${app.auth.session-hours:8}") long horas) {
        usuarios = u;
        sessoes = s;
        encoder = e;
        duracao = Duration.ofHours(horas);
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        Usuario u = usuarios.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BusinessException("E-mail ou senha inválidos."));
        if (!u.isAtivo())
            throw new BusinessException("Usuário desativado. Contate um administrador.");
        if (!encoder.matches(req.senha(), u.getSenhaHash()))
            throw new BusinessException("E-mail ou senha inválidos.");
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        SessaoUsuario s = new SessaoUsuario();
        s.setUsuario(u);
        s.setTokenHash(hash(token));
        s.setExpiraEm(OffsetDateTime.now().plus(duracao));
        sessoes.save(s);
        return new LoginResponse(token, "Bearer", s.getExpiraEm(), UsuarioResponse.de(u));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest r) {
        if (usuarios.existsByEmailIgnoreCase(r.email()))
            throw new ResourceConflictException("E-mail já cadastrado.");
        Usuario u = new Usuario();
        u.setNome(r.nome());
        u.setEmail(r.email());
        u.setSenhaHash(encoder.encode(r.senha()));
        u.setPapel(r.papel());
        return UsuarioResponse.de(usuarios.save(u));
    }

    @Transactional
    public UsuarioResponse alterarAtivacao(UUID id, boolean ativo) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        u.setAtivo(ativo);
        if (!ativo)
            sessoes.findByUsuarioIdAndEncerradaEmIsNull(id).forEach(SessaoUsuario::encerrar);
        return UsuarioResponse.de(u);
    }

    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream().map(UsuarioResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public Usuario usuarioDaSessao(String token) {
        SessaoUsuario s = sessoes.findByTokenHash(hash(token))
                .orElseThrow(() -> new BusinessException("Sessão inválida."));
        if (s.getEncerradaEm() != null || !s.getExpiraEm().isAfter(OffsetDateTime.now()) || !s.getUsuario().isAtivo())
            throw new BusinessException("Sessão expirada ou encerrada.");
        return s.getUsuario();
    }

    @Transactional
    public void logout(String token) {
        sessoes.findByTokenHash(hash(token)).ifPresent(SessaoUsuario::encerrar);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

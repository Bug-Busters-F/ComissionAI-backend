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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
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
                .orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos."));
        if (!u.isAtivo())
            throw new BadCredentialsException("Usuário desativado. Contate um administrador.");
        if (!encoder.matches(req.senha(), u.getSenhaHash()))
            throw new BadCredentialsException("E-mail ou senha inválidos.");
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
        if (!ativo) usuarios.bloquearAdministradores();
        Usuario u = obter(id);
        if (!ativo) validarRetiradaDeAcesso(u);
        u.setAtivo(ativo);
        if (!ativo)
            sessoes.findByUsuarioIdAndEncerradaEmIsNull(id).forEach(SessaoUsuario::encerrar);
        return UsuarioResponse.de(u);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar(UUID id) {
        return UsuarioResponse.de(obter(id));
    }

    @Transactional
    public UsuarioResponse atualizar(UUID id, AtualizarUsuarioRequest request) {
        Usuario usuario = obter(id);
        validarEmailDisponivel(request.email(), id);
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public UsuarioResponse alterarPerfil(UUID id, PapelUsuario papel) {
        usuarios.bloquearAdministradores();
        Usuario usuario = obter(id);
        if (usuario.getPapel() != papel) {
            validarPropriaConta(usuario);
            if (papel != PapelUsuario.ADMINISTRADOR) validarUltimoAdministrador(usuario);
        }
        usuario.setPapel(papel);
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public void excluirLogicamente(UUID id) {
        alterarAtivacao(id, false);
    }

    @Transactional
    public UsuarioResponse atualizarMeuPerfil(Usuario atual, AtualizarUsuarioRequest request) {
        Usuario usuario = obter(atual.getId());
        validarEmailDisponivel(request.email(), usuario.getId());
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public void alterarMinhaSenha(Usuario atual, AlterarSenhaRequest request) {
        Usuario usuario = obter(atual.getId());
        if (!encoder.matches(request.senhaAtual(), usuario.getSenhaHash()))
            throw new BusinessException("Senha atual inválida.");
        usuario.setSenhaHash(encoder.encode(request.novaSenha()));
        // Uma senha nova revoga outros acessos sem interromper a chamada atual.
        sessoes.findByUsuarioIdAndEncerradaEmIsNull(usuario.getId()).forEach(SessaoUsuario::encerrar);
    }

    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream().map(UsuarioResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public Usuario usuarioDaSessao(String token) {
        SessaoUsuario s = sessoes.findByTokenHash(hash(token))
                .orElseThrow(() -> new BadCredentialsException("Sessão inválida."));
        if (s.getEncerradaEm() != null || !s.getExpiraEm().isAfter(OffsetDateTime.now()) || !s.getUsuario().isAtivo())
            throw new BadCredentialsException("Sessão expirada ou encerrada.");
        return s.getUsuario();
    }

    @Transactional
    public void logout(String token) {
        sessoes.findByTokenHash(hash(token)).ifPresent(SessaoUsuario::encerrar);
    }

    public List<PerfilResponse> perfis() {
        return Arrays.stream(PapelUsuario.values())
                .map(p -> new PerfilResponse(p, p.getNome(), PerfilPermissoes.permissoes(p))).toList();
    }

    private void validarRetiradaDeAcesso(Usuario usuario) {
        validarPropriaConta(usuario);
        validarUltimoAdministrador(usuario);
    }

    private void validarPropriaConta(Usuario usuario) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Usuario atual
                && atual.getId().equals(usuario.getId())) {
            throw new ResourceConflictException("Não é permitido desativar ou alterar o perfil da própria conta.");
        }
    }

    private void validarUltimoAdministrador(Usuario usuario) {
        if (usuario.isAtivo() && usuario.getPapel() == PapelUsuario.ADMINISTRADOR
                && usuarios.countByPapelAndAtivoTrue(PapelUsuario.ADMINISTRADOR) <= 1) {
            throw new ResourceConflictException("Mantenha pelo menos um administrador ativo.");
        }
    }

    private Usuario obter(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }

    private void validarEmailDisponivel(String email, UUID idAtual) {
        usuarios.findByEmailIgnoreCase(email).filter(u -> !u.getId().equals(idAtual))
                .ifPresent(u -> { throw new ResourceConflictException("E-mail já cadastrado."); });
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

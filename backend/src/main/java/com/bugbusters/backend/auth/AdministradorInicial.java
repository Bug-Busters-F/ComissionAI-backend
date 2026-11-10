package com.bugbusters.backend.auth;

import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdministradorInicial implements CommandLineRunner {
    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    @Value("${app.auth.admin.email:admin@comissai.local}")
    String email;
    @Value("${app.auth.admin.password:Admin@123}")
    String senha;

    public AdministradorInicial(UsuarioRepository r, PasswordEncoder e) {
        repo = r;
        encoder = e;
    }

    public void run(String... a) {
        if (!repo.existsByEmailIgnoreCase(email)) {
            Usuario u = new Usuario();
            u.setNome("Administrador inicial");
            u.setEmail(email);
            u.setSenhaHash(encoder.encode(senha));
            u.setPapel(PapelUsuario.ADMINISTRADOR);
            repo.save(u);
        }
    }
}

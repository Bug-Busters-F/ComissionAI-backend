package com.bugbusters.backend.auth.dto;
import com.bugbusters.backend.auth.PapelUsuario;
import java.util.List;
public record PerfilResponse(PapelUsuario id, String nome, List<String> permissoes) {}

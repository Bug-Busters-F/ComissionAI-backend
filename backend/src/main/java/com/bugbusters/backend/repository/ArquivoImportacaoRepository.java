package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.ArquivoImportacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArquivoImportacaoRepository extends JpaRepository<ArquivoImportacao, UUID> {
    Optional<ArquivoImportacao> findByHashConteudo(String hashConteudo);
}

package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.HistoricoVersaoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HistoricoVersaoRegistroRepository extends JpaRepository<HistoricoVersaoRegistro, UUID> {
    List<HistoricoVersaoRegistro> findByTipoBaseAndRegistroIdOrderByVersaoDesc(String tipoBase, UUID registroId);

    List<HistoricoVersaoRegistro> findByTipoBaseAndChaveNegocioOrderByVersaoDesc(String tipoBase, String chaveNegocio);
}

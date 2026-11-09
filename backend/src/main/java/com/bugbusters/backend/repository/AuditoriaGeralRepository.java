package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.AuditoriaGeral;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditoriaGeralRepository extends JpaRepository<AuditoriaGeral, UUID>, JpaSpecificationExecutor<AuditoriaGeral> {

    Optional<AuditoriaGeral> findByProtocolo(UUID protocolo);

    List<AuditoriaGeral> findAllByTipoEntidadeAndEntidadeIdOrderByDataHoraDesc(
            TipoEntidadeAuditoria tipoEntidade,
            String entidadeId
    );

    Page<AuditoriaGeral> findAllByTipoEntidadeAndEntidadeIdOrderByDataHoraDesc(
            TipoEntidadeAuditoria tipoEntidade,
            String entidadeId,
            Pageable pageable
    );

    List<AuditoriaGeral> findAllByUsuarioOrderByDataHoraDesc(String usuario);

    long countByTipoAcao(TipoAcaoAuditoria tipoAcao);

    long countByDataHoraBetween(OffsetDateTime inicio, OffsetDateTime fim);
}


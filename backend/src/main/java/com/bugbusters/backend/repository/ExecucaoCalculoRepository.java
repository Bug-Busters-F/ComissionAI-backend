package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.ExecucaoCalculo;
import com.bugbusters.backend.model.StatusExecucaoCalculo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ExecucaoCalculoRepository extends JpaRepository<ExecucaoCalculo, UUID> {
    @org.springframework.data.jpa.repository.Query("select e.competencia from ExecucaoCalculo e where e.id = :id")
    Optional<String> competencia(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from ExecucaoCalculo e where e.id = :id")
    Optional<ExecucaoCalculo> bloquear(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ExecucaoCalculo e set e.itensProcessados = :itens where e.id = :id and e.tentativa = :tentativa and e.status = com.bugbusters.backend.model.StatusExecucaoCalculo.PROCESSANDO and e.itensProcessados < :itens")
    int progresso(@org.springframework.data.repository.query.Param("id") UUID id,
                  @org.springframework.data.repository.query.Param("tentativa") UUID tentativa,
                  @org.springframework.data.repository.query.Param("itens") int itens);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ExecucaoCalculo e set e.status = com.bugbusters.backend.model.StatusExecucaoCalculo.FALHOU, e.falha = :falha, e.concluidoEm = :agora where e.id = :id and e.tentativa = :tentativa and e.status in (com.bugbusters.backend.model.StatusExecucaoCalculo.SOLICITADA,com.bugbusters.backend.model.StatusExecucaoCalculo.PROCESSANDO)")
    int falhar(@org.springframework.data.repository.query.Param("id") UUID id,
               @org.springframework.data.repository.query.Param("tentativa") UUID tentativa,
               @org.springframework.data.repository.query.Param("falha") String falha,
               @org.springframework.data.repository.query.Param("agora") java.time.OffsetDateTime agora);

    Optional<ExecucaoCalculo> findByChaveIdempotencia(String chaveIdempotencia);
    Optional<ExecucaoCalculo> findFirstByCompetenciaAndStatusInOrderBySolicitadoEmDesc(String competencia, Collection<StatusExecucaoCalculo> status);
}

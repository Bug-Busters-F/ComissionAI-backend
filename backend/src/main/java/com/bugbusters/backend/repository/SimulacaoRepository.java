package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.Simulacao;
import com.bugbusters.backend.model.StatusAceiteSimulacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SimulacaoRepository extends JpaRepository<Simulacao, UUID>, JpaSpecificationExecutor<Simulacao> {

    Optional<Simulacao> findByProtocolo(UUID protocolo);

    Page<Simulacao> findAllByOrderByExecutadoEmDesc(Pageable pageable);

    Page<Simulacao> findByCompetenciaOrderByExecutadoEmDesc(String competencia, Pageable pageable);

    Page<Simulacao> findByCampanhaIdOrderByExecutadoEmDesc(Long campanhaId, Pageable pageable);

    Page<Simulacao> findByCompetenciaAndCampanhaIdOrderByExecutadoEmDesc(String competencia, Long campanhaId, Pageable pageable);

    Optional<Simulacao> findFirstByCampanhaIdOrderByExecutadoEmDesc(Long campanhaId);

    Optional<Simulacao> findFirstByCampanhaVersaoIdOrderByExecutadoEmDesc(Long campanhaVersaoId);

    Optional<Simulacao> findFirstByCampanhaIdAndStatusAceiteOrderByAceitoEmDesc(Long campanhaId, StatusAceiteSimulacao statusAceite);

    List<Simulacao> findAllByCampanhaIdAndStatusAceite(Long campanhaId, StatusAceiteSimulacao statusAceite);

    List<Simulacao> findAllByCampanhaVersaoId(Long campanhaVersaoId);

    List<Simulacao> findByCompetenciaInAndStatusAceiteIn(Collection<String> competencias, Collection<StatusAceiteSimulacao> statusList);

    List<Simulacao> findByCampanhaIdInAndStatusAceiteIn(Collection<Long> campanhaIds, Collection<StatusAceiteSimulacao> statusList);

    List<Simulacao> findTop10ByQtdVendasComImpedimentoOrderByExecutadoEmDesc(int qtdVendasComImpedimento);
}

package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.CampanhaApuracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampanhaApuracaoRepository extends JpaRepository<CampanhaApuracao, UUID> {

    Optional<CampanhaApuracao> findFirstByCampanhaIdOrderByExecutadoEmDesc(Long campanhaId);

    List<CampanhaApuracao> findAllByCampanhaIdOrderByExecutadoEmDesc(Long campanhaId);

    Optional<CampanhaApuracao> findByProtocolo(UUID protocolo);
}


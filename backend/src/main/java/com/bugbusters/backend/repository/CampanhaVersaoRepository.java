package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.StatusVersaoCampanha;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CampanhaVersaoRepository extends JpaRepository<CampanhaVersao, Long> {
    Optional<CampanhaVersao> findFirstByCampanhaIdAndStatus(Long campanhaId, StatusVersaoCampanha status);
    List<CampanhaVersao> findAllByCampanhaIdOrderByNumeroDesc(Long campanhaId);
}

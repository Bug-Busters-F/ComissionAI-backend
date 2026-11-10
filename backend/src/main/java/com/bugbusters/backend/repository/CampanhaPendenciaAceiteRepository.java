package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.CampanhaPendenciaAceite;
import com.bugbusters.backend.model.StatusAceitePendencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampanhaPendenciaAceiteRepository extends JpaRepository<CampanhaPendenciaAceite, Long> {

    List<CampanhaPendenciaAceite> findAllByCampanhaIdAndStatus(Long campanhaId, StatusAceitePendencia status);

    List<CampanhaPendenciaAceite> findAllByCampanhaVersaoIdAndStatus(Long campanhaVersaoId, StatusAceitePendencia status);

    List<CampanhaPendenciaAceite> findAllByRegraIdAndStatus(Long regraId, StatusAceitePendencia status);

    List<CampanhaPendenciaAceite> findAllByCampanhaIdOrderByAceitoEmDesc(Long campanhaId);

    List<CampanhaPendenciaAceite> findAllByCampanhaVersaoIdOrderByAceitoEmDesc(Long campanhaVersaoId);

    Optional<CampanhaPendenciaAceite> findByCampanhaVersaoIdAndPendenciaIdentificadorAndStatus(
            Long campanhaVersaoId, String pendenciaIdentificador, StatusAceitePendencia status
    );

    Optional<CampanhaPendenciaAceite> findByCampanhaIdAndPendenciaIdentificadorAndStatus(
            Long campanhaId, String pendenciaIdentificador, StatusAceitePendencia status
    );
}


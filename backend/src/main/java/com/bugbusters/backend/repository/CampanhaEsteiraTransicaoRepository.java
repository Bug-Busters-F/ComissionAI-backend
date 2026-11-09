package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.CampanhaEsteiraTransicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampanhaEsteiraTransicaoRepository extends JpaRepository<CampanhaEsteiraTransicao, Long> {

    List<CampanhaEsteiraTransicao> findAllByCampanhaVersaoIdOrderByCriadoEmDesc(Long campanhaVersaoId);

    List<CampanhaEsteiraTransicao> findAllByCampanhaVersaoCampanhaIdOrderByCriadoEmDesc(Long campanhaId);
}


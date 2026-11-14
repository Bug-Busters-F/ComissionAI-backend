package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.CampanhaEsteiraTransicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampanhaEsteiraTransicaoRepository extends JpaRepository<CampanhaEsteiraTransicao, Long> {

    List<CampanhaEsteiraTransicao> findAllByCampanhaVersaoIdOrderByCriadoEmDescIdDesc(Long campanhaVersaoId);

    List<CampanhaEsteiraTransicao> findAllByCampanhaVersaoCampanhaIdOrderByCriadoEmDescIdDesc(Long campanhaId);
}


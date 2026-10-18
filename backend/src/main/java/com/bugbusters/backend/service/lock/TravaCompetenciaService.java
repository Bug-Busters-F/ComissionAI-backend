package com.bugbusters.backend.service.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravaCompetenciaService implements TravaCompetencia {
    private static final Logger log = LoggerFactory.getLogger(TravaCompetenciaService.class);
    private final TravaCompetenciaRepository repository;

    public TravaCompetenciaService(TravaCompetenciaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void adquirirTrava(String competencia) {
        if (competencia == null || competencia.isBlank()) {
            return;
        }
        String comp = competencia.trim();
        if (comp.length() > 7) {
            comp = comp.substring(0, 7);
        }
        if (!repository.existsById(comp)) {
            try {
                repository.saveAndFlush(new TravaCompetenciaEntity(comp));
            } catch (Exception ignored) {
                // Registro já inserido concorrentemente por outra transação
            }
        }
        repository.findByCompetenciaForUpdate(comp);
        log.debug("Trava de competência adquirida para '{}'", comp);
    }
}

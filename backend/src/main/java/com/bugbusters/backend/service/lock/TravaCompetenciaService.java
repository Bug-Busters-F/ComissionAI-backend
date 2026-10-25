package com.bugbusters.backend.service.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravaCompetenciaService implements TravaCompetencia {
    private static final Logger log = LoggerFactory.getLogger(TravaCompetenciaService.class);
    private final TravaCompetenciaRepository repository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public TravaCompetenciaService(TravaCompetenciaRepository repository, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
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
        String database = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        // Do not catch a uniqueness violation inside the transaction: PostgreSQL would leave it aborted.
        if ("PostgreSQL".equals(database)) {
            jdbc.update("INSERT INTO tb_trava_competencia (competencia, bloqueado_em) VALUES (?, CURRENT_TIMESTAMP) ON CONFLICT (competencia) DO NOTHING", comp);
        } else if ("H2".equals(database)) {
            jdbc.update("MERGE INTO tb_trava_competencia (competencia, bloqueado_em) KEY (competencia) VALUES (?, CURRENT_TIMESTAMP)", comp);
        } else {
            throw new IllegalStateException("Banco não suportado para a trava de competência.");
        }
        repository.findByCompetenciaForUpdate(comp).orElseThrow(() -> new IllegalStateException("Não foi possível adquirir a trava da competência."));
        log.debug("Trava de competência adquirida para '{}'", comp);
    }
}

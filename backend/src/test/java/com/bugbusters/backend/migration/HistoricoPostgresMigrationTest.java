package com.bugbusters.backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs against a disposable PostgreSQL database, in a new schema per test. Never repairs or cleans an existing schema. */
@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class HistoricoPostgresMigrationTest {
    private final String url = System.getenv("MIGRATION_TEST_DB_URL");
    private final String user = System.getenv().getOrDefault("MIGRATION_TEST_DB_USER", "postgres");
    private final String password = System.getenv().getOrDefault("MIGRATION_TEST_DB_PASSWORD", "postgres");
    private String schema() { return "history_test_" + UUID.randomUUID().toString().replace("-", ""); }
    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target(target).load();
    }
    private Connection connection(String schema) throws Exception {
        Connection c = DriverManager.getConnection(url,user,password);
        c.createStatement().execute("SET search_path TO " + schema);
        return c;
    }
    private String scalar(Connection c, String sql) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }
    private UUID simulation(Connection c, long version, String author) throws Exception {
        var values = new java.util.LinkedHashMap<String,Object>();
        try (var statement = c.createStatement(); var columns = statement.executeQuery(
                "SELECT column_name,data_type FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_simulacao' AND is_nullable='NO' AND column_default IS NULL ORDER BY ordinal_position")) {
            while (columns.next()) {
                Object value = switch(columns.getString(2)) {
                    case "uuid" -> UUID.randomUUID();
                    case "numeric" -> java.math.BigDecimal.ZERO;
                    case "integer", "bigint" -> 0;
                    case "date" -> java.time.LocalDate.of(2038,1,1);
                    case "text", "character varying" -> "fixture";
                    default -> throw new IllegalStateException("Unsupported fixture column: " + columns.getString(2));
                };
                values.put(columns.getString(1),value);
            }
        }
        values.put("campanha_id",900L); values.put("campanha_versao_id",version);
        values.put("status_aceite","ACEITA"); values.put("aceito_por",author);
        String placeholders = String.join(",", java.util.Collections.nCopies(values.size(),"?"));
        try (var insert = c.prepareStatement("INSERT INTO tb_simulacao ("+String.join(",",values.keySet())+") VALUES ("+placeholders+")")) {
            int index=1; for (Object value:values.values()) insert.setObject(index++,value);
            insert.executeUpdate();
        }
        return (UUID) values.get("id");
    }
    @Test void registraOrigemDePropostaExistenteSemAlterarPublicacao() throws Exception {
        String schema=schema(); flyway(schema,"26").migrate();
        try (var c=connection(schema); var s=c.createStatement()) {
            s.execute("INSERT INTO tb_campanha(id,titulo,texto_original,data_inicio,data_fim,estado) VALUES (990,'Vigente','Texto','2040-01-01','2040-01-31','ATIVA')");
            s.execute("INSERT INTO tb_campanha_versao(id,campanha_id,numero,status,titulo,texto_original,data_inicio,data_fim) VALUES (991,990,1,'PUBLICADA','Vigente','Texto','2040-01-01','2040-01-31'), (992,990,2,'PROPOSTA','Proposta','Texto editado','2040-01-01','2040-01-31')");
            s.execute("UPDATE tb_campanha SET versao_vigente_id=991,versao_proposta_id=992 WHERE id=990");
        }
        var all=flyway(schema,"27"); assertThat(all.migrate().migrationsExecuted).isEqualTo(1); all.validate();
        try (var c=connection(schema)) {
            assertThat(scalar(c,"SELECT versao_anterior_id FROM tb_campanha_versao WHERE id=992")).isEqualTo("991");
            assertThat(scalar(c,"SELECT titulo FROM tb_campanha_versao WHERE id=991")).isEqualTo("Vigente");
        }
    }

    @Test void atualizaV25ParaV26PreservandoFechamentoEHistorico() throws Exception {
        String schema=schema(); flyway(schema,"25").migrate();
        UUID id=UUID.randomUUID();
        try (var c=connection(schema); var s=c.createStatement()) {
            s.execute("INSERT INTO tb_fechamento_competencia(id,competencia,protocolo,status) VALUES ('"+id+"','2040-01','"+UUID.randomUUID()+"','CONCLUIDO')");
            s.execute("INSERT INTO tb_campanha(id,titulo,texto_original,data_inicio,data_fim,estado) VALUES (980,'Antes da execução','Texto','2040-01-01','2040-01-31','DRAFT')");
        }
        var all=flyway(schema,"26"); assertThat(all.migrate().migrationsExecuted).isEqualTo(1); all.validate();
        try (var c=connection(schema)) {
            assertThat(scalar(c,"SELECT status FROM tb_fechamento_competencia WHERE id='"+id+"'")).isEqualTo("CONCLUIDO");
            assertThat(scalar(c,"SELECT titulo FROM tb_campanha WHERE id=980")).isEqualTo("Antes da execução");
            assertThat(scalar(c,"SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_execucao_calculo' AND column_name IN ('tentativa','referencia_dados','referencia_regras','itens_processados')")).isEqualTo("4");
        }
    }

    @Test void atualizaV24ParaV25PreservandoHistoricoEProgresso() throws Exception {
        String schema = schema(); flyway(schema,"24").migrate();
        try (var c=connection(schema); var s=c.createStatement()) {
            s.execute("INSERT INTO tb_campanha(id,titulo,texto_original,data_inicio,data_fim,estado) VALUES (950,'Publicada','Texto preservado','2040-01-01','2040-01-31','ATIVA')");
            s.execute("INSERT INTO tb_campanha_versao(id,campanha_id,numero,status,titulo,texto_original,data_inicio,data_fim,ultima_etapa,etapa_mais_avancada,publicado_por) VALUES (951,950,1,'PUBLICADA','Versão histórica','Texto','2040-01-01','2040-01-31',1,4,'Autor anterior')");
            s.execute("INSERT INTO tb_regra(id,campanha_id,campanha_versao_id,nome,taxa,data_inicio,data_fim,status,identificador_logico,python_equivalente,rastreabilidade_json) VALUES (952,950,951,'Preservada',0.05,'2040-01-01','2040-01-31','ATIVA','one','codigo_anterior','{\"camposManuais\":[\"taxaPercentual\"]}')");
        }
        var all=flyway(schema,"25"); assertThat(all.migrate().migrationsExecuted).isEqualTo(1); all.validate();
        try (var c=connection(schema)) {
            assertThat(scalar(c,"SELECT python_equivalente FROM tb_regra WHERE id=952")).isEqualTo("codigo_anterior");
            assertThat(scalar(c,"SELECT rastreabilidade_json FROM tb_regra WHERE id=952")).contains("taxaPercentual");
            assertThat(scalar(c,"SELECT publicado_por FROM tb_campanha_versao WHERE id=951")).isEqualTo("Autor anterior");
            assertThat(scalar(c,"SELECT etapa_mais_avancada FROM tb_campanha_versao WHERE id=951")).isEqualTo("4");
            assertThat(scalar(c,"SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_resultado_calculo' AND column_name IN ('fechamento_id','versao_campanha_numero')")).isEqualTo("2");
        }
    }

    @Test void instalaTodasAsMigracoesSemVersoesDuplicadas() throws Exception {
        String schema = schema(); var all = flyway(schema,"24");
        all.migrate(); all.validate();
        assertThat(all.info().current().getVersion().toString()).isEqualTo("24");
        try (var c = connection(schema)) {
            assertThat(scalar(c,"SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'tb_campanha_versao' AND column_name IN ('versao_anterior_id','publicado_por','simulacao_aceita_id','ultima_etapa','etapa_mais_avancada')")).isEqualTo("5");
        }
    }
    @Test void atualizaV23PreservandoDadosProgressoEArtefatosEIgnorandoVersaoCancelada() throws Exception {
        String schema = schema(); flyway(schema,"23").migrate();
        UUID publishedSimulation;
        try (var c = connection(schema); var s = c.createStatement()) {
            s.execute("INSERT INTO tb_campanha(id,titulo,texto_original,data_inicio,data_fim,estado) VALUES (900,'Campanha preservada','Texto anterior','2038-01-01','2038-01-31','ATIVA')");
            s.execute("INSERT INTO tb_campanha_versao(id,campanha_id,numero,status,titulo,texto_original,data_inicio,data_fim,ultima_etapa,etapa_mais_avancada) VALUES (901,900,1,'PUBLICADA','Versão 1','Texto 1','2038-01-01','2038-01-31',1,4),(902,900,2,'CANCELADA','Descartada','Cancelada','2038-01-01','2038-01-31',1,1),(903,900,3,'PUBLICADA','Versão 3','Texto 3','2038-01-01','2038-01-31',2,4)");
            s.execute("INSERT INTO tb_campanha_versao(id,campanha_id,numero,status,titulo,texto_original,data_inicio,data_fim) VALUES (904,900,4,'PROPOSTA','Em edição','Proposta','2038-01-01','2038-01-31')");
            publishedSimulation = simulation(c,901,"Autor publicado");
            UUID proposalSimulation = simulation(c,904,"Autor da proposta");
            s.execute("UPDATE tb_campanha SET versao_vigente_id = 903, versao_proposta_id = 904, simulacao_aceita_id = '"+proposalSimulation+"' WHERE id = 900");
            s.execute("INSERT INTO tb_regra(id,campanha_id,campanha_versao_id,nome,bloco_id,taxa,data_inicio,data_fim,status,explicacao,python_equivalente,rastreabilidade_json) VALUES (910,900,901,'Primeira','one',0.035,'2038-01-01','2038-01-31','INATIVA','Explicação preservada','codigo_v1','{\"camposManuais\":[\"taxaPercentual\"]}'),(911,900,903,'Legada',NULL,0.05,'2038-01-01','2038-01-31','ATIVA','Outra explicação','codigo_v3',NULL)");
        }
        var all = flyway(schema,"24"); assertThat(all.migrate().migrationsExecuted).isEqualTo(1); all.validate();
        try (var c = connection(schema)) {
            assertThat(scalar(c,"SELECT versao_anterior_id FROM tb_campanha_versao WHERE id=903")).isEqualTo("901");
            assertThat(scalar(c,"SELECT simulacao_aceita_id FROM tb_campanha_versao WHERE id=901")).isEqualTo(publishedSimulation.toString());
            assertThat(scalar(c,"SELECT publicado_por FROM tb_campanha_versao WHERE id=901")).isEqualTo("Autor publicado");
            assertThat(scalar(c,"SELECT simulacao_aceita_id FROM tb_campanha_versao WHERE id=903")).isNull();
            assertThat(scalar(c,"SELECT publicado_por FROM tb_campanha_versao WHERE id=903")).isNull();
            assertThat(scalar(c,"SELECT simulacao_aceita_id FROM tb_campanha_versao WHERE id=904")).isNull();
            assertThat(scalar(c,"SELECT identificador_logico FROM tb_regra WHERE id=910")).isEqualTo("one");
            assertThat(scalar(c,"SELECT identificador_logico FROM tb_regra WHERE id=911")).isEqualTo("legado-911");
            assertThat(scalar(c,"SELECT titulo FROM tb_campanha WHERE id=900")).isEqualTo("Campanha preservada");
            assertThat(scalar(c,"SELECT python_equivalente FROM tb_regra WHERE id=910")).isEqualTo("codigo_v1");
            assertThat(scalar(c,"SELECT explicacao FROM tb_regra WHERE id=910")).isEqualTo("Explicação preservada");
            assertThat(scalar(c,"SELECT rastreabilidade_json FROM tb_regra WHERE id=910")).contains("taxaPercentual");
            assertThat(scalar(c,"SELECT etapa_mais_avancada FROM tb_campanha_versao WHERE id=903")).isEqualTo("4");
            assertThat(scalar(c,"SELECT ultima_etapa FROM tb_campanha_versao WHERE id=903")).isEqualTo("2");
        }
    }
}

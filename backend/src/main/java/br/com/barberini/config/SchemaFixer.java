package br.com.barberini.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * O ddl-auto=update cria a restrição do enum de status (ENUM nativo no H2, CHECK no Postgres)
 * e nunca a atualiza quando novos valores entram. Sem isso, gravar FINALIZADO/NAO_COMPARECEU
 * quebra em bancos que já existiam. Converte a coluna para varchar simples.
 * <p>
 * Também cria/backfill de {@code barbearia_id} — o Hibernate não consegue adicionar
 * NOT NULL em tabelas com linhas existentes, e o seed morre com "column does not exist".
 */
@Configuration
public class SchemaFixer {

    private static final Logger log = LoggerFactory.getLogger(SchemaFixer.class);

    private static final String[] TABELAS_TENANT = {
            "barbeiros", "servicos", "bloqueios_horario", "agendamentos"
    };

    @Bean
    @Order(0)
    CommandLineRunner normalizarColunaStatus(JdbcTemplate jdbc) {
        return args -> {
            removerChecksObsoletos(jdbc);
            converterParaVarchar(jdbc);
            garantirColunasBarbearia(jdbc);
            vincularOrfaosAoDemo(jdbc);
        };
    }

    private void removerChecksObsoletos(JdbcTemplate jdbc) {
        try {
            List<String> obsoletos = jdbc.queryForList("""
                    select tc.constraint_name
                    from information_schema.table_constraints tc
                    join information_schema.check_constraints cc
                      on cc.constraint_name = tc.constraint_name
                     and cc.constraint_schema = tc.constraint_schema
                    where lower(tc.table_name) = 'agendamentos'
                      and tc.constraint_type = 'CHECK'
                      and cc.check_clause like '%CONFIRMADO%'
                      and cc.check_clause not like '%NAO_COMPARECEU%'
                    """, String.class);

            for (String nome : obsoletos) {
                jdbc.execute("alter table agendamentos drop constraint \"" + nome + "\"");
                log.info("Restrição de status desatualizada removida: {}", nome);
            }
        } catch (Exception e) {
            log.warn("Não foi possível revisar as restrições de status: {}", e.getMessage());
        }
    }

    private void converterParaVarchar(JdbcTemplate jdbc) {
        try {
            String tipo = jdbc.queryForObject("""
                    select data_type from information_schema.columns
                    where lower(table_name) = 'agendamentos' and lower(column_name) = 'status'
                    """, String.class);
            if (tipo == null || tipo.toLowerCase().contains("char")) return;

            String banco = jdbc.execute(
                    (ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
            String sql = banco != null && banco.toLowerCase().contains("postgre")
                    ? "alter table agendamentos alter column status type varchar(20)"
                    : "alter table agendamentos alter column status varchar(20) not null";
            jdbc.execute(sql);
            log.info("Coluna status convertida de {} para varchar", tipo);
        } catch (Exception e) {
            log.warn("Não foi possível converter a coluna status: {}", e.getMessage());
        }
    }

    /** Hibernate ddl-auto falha ao ADD NOT NULL com dados; criamos nullable → backfill → NOT NULL. */
    private void garantirColunasBarbearia(JdbcTemplate jdbc) {
        if (!tabelaExiste(jdbc, "barbearias")) return;

        Long demoId = garantirDemo(jdbc);
        if (demoId == null) return;

        for (String tabela : TABELAS_TENANT) {
            if (!tabelaExiste(jdbc, tabela)) continue;
            try {
                if (!colunaExiste(jdbc, tabela, "barbearia_id")) {
                    jdbc.execute("alter table " + tabela + " add column barbearia_id bigint");
                    log.info("Coluna {}.barbearia_id criada (nullable)", tabela);
                }
                int n = jdbc.update(
                        "update " + tabela + " set barbearia_id = ? where barbearia_id is null",
                        demoId);
                if (n > 0) {
                    log.info("Backfill {} órfãos em {}.barbearia_id → demo {}", n, tabela, demoId);
                }
                if (isPostgres(jdbc) && colunaNullable(jdbc, tabela, "barbearia_id")) {
                    jdbc.execute("alter table " + tabela + " alter column barbearia_id set not null");
                }
                garantirFk(jdbc, tabela, "barbearia_id", "fk_" + tabela + "_barbearia");
            } catch (Exception e) {
                log.warn("Falha ao garantir barbearia_id em {}: {}", tabela, e.getMessage());
            }
        }

        if (tabelaExiste(jdbc, "usuarios") && !colunaExiste(jdbc, "usuarios", "barbearia_id")) {
            try {
                jdbc.execute("alter table usuarios add column barbearia_id bigint");
                log.info("Coluna usuarios.barbearia_id criada");
            } catch (Exception e) {
                log.warn("Falha ao criar usuarios.barbearia_id: {}", e.getMessage());
            }
        }
    }

    private void vincularOrfaosAoDemo(JdbcTemplate jdbc) {
        try {
            if (!tabelaExiste(jdbc, "barbearias")) return;

            Long demoId = garantirDemo(jdbc);
            if (demoId == null) return;

            for (String tabela : TABELAS_TENANT) {
                atualizarOrfaos(jdbc, tabela, demoId);
            }

            if (colunaExiste(jdbc, "usuarios", "barbearia_id")) {
                int n = jdbc.update("""
                        update usuarios set barbearia_id = ?
                        where upper(papel) = 'DONO' and barbearia_id is null
                        """, demoId);
                if (n > 0) log.info("Vinculados {} donos órfãos à loja demo", n);
            }
        } catch (Exception e) {
            log.warn("Não foi possível vincular órfãos à loja demo: {}", e.getMessage());
        }
    }

    private Long garantirDemo(JdbcTemplate jdbc) {
        try {
            List<Long> ids = jdbc.query(
                    "select id from barbearias where lower(slug) = 'demo'",
                    (rs, i) -> rs.getLong(1));
            if (!ids.isEmpty()) return ids.get(0);

            jdbc.update("""
                    insert into barbearias (nome, slug, plano, status_assinatura, ativo, criado_em)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    "Barbearia Demo", "demo", "TRIAL", "TRIAL", true,
                    Timestamp.valueOf(LocalDateTime.now()));

            ids = jdbc.query(
                    "select id from barbearias where lower(slug) = 'demo'",
                    (rs, i) -> rs.getLong(1));
            log.info("Loja demo criada para migração multi-tenant");
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.warn("Falha ao garantir loja demo: {}", e.getMessage());
            return null;
        }
    }

    private void atualizarOrfaos(JdbcTemplate jdbc, String tabela, Long demoId) {
        if (!colunaExiste(jdbc, tabela, "barbearia_id")) return;
        try {
            int n = jdbc.update("update " + tabela + " set barbearia_id = ? where barbearia_id is null", demoId);
            if (n > 0) log.info("Vinculados {} registros órfãos de {} à loja demo", n, tabela);
        } catch (Exception e) {
            log.warn("Não foi possível atualizar órfãos em {}: {}", tabela, e.getMessage());
        }
    }

    private void garantirFk(JdbcTemplate jdbc, String tabela, String coluna, String nomeFk) {
        if (!isPostgres(jdbc)) return;
        try {
            Integer existe = jdbc.queryForObject("""
                    select count(*) from pg_constraint
                    where conname = ? and contype = 'f'
                    """, Integer.class, nomeFk);
            if (existe != null && existe > 0) return;
            jdbc.execute("alter table " + tabela
                    + " add constraint " + nomeFk
                    + " foreign key (" + coluna + ") references barbearias(id)");
            log.info("FK {} criada", nomeFk);
        } catch (Exception e) {
            log.warn("Não foi possível criar FK {}: {}", nomeFk, e.getMessage());
        }
    }

    private boolean colunaNullable(JdbcTemplate jdbc, String tabela, String coluna) {
        try {
            String n = jdbc.queryForObject("""
                    select is_nullable from information_schema.columns
                    where lower(table_name) = ? and lower(column_name) = ?
                    """, String.class, tabela.toLowerCase(), coluna.toLowerCase());
            return "YES".equalsIgnoreCase(n);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPostgres(JdbcTemplate jdbc) {
        try {
            String banco = jdbc.execute(
                    (ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
            return banco != null && banco.toLowerCase().contains("postgre");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tabelaExiste(JdbcTemplate jdbc, String tabela) {
        try {
            Integer c = jdbc.queryForObject("""
                    select count(*) from information_schema.tables
                    where lower(table_name) = ?
                    """, Integer.class, tabela.toLowerCase());
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean colunaExiste(JdbcTemplate jdbc, String tabela, String coluna) {
        try {
            Integer c = jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where lower(table_name) = ? and lower(column_name) = ?
                    """, Integer.class, tabela.toLowerCase(), coluna.toLowerCase());
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }
}

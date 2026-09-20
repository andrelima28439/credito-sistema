package com.credito.relatorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class RelatorioJobTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Path SAIDA = Path.of(System.getProperty("java.io.tmpdir"), "relatorio-job-test");

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.datasource.solicitacao.url", postgres::getJdbcUrl);
        registry.add("app.datasource.solicitacao.username", postgres::getUsername);
        registry.add("app.datasource.solicitacao.password", postgres::getPassword);
        registry.add("app.relatorio.diretorio", () -> SAIDA.toString());
    }

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    Job relatorioDiarioJob;

    @Autowired
    JdbcTemplate solicitacaoJdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${app.relatorio.diretorio}")
    String diretorio;

    @BeforeEach
    void fixture() {
        solicitacaoJdbcTemplate.execute("DROP TABLE IF EXISTS solicitacao");
        solicitacaoJdbcTemplate.execute(
                """
        CREATE TABLE solicitacao (
          id UUID PRIMARY KEY,
          cliente_id VARCHAR(255) NOT NULL,
          valor NUMERIC(15, 2) NOT NULL,
          status VARCHAR(20) NOT NULL,
          data_criacao TIMESTAMPTZ NOT NULL,
          data_decisao TIMESTAMPTZ,
          aprovador_id VARCHAR(255)
        )
        """);
        LocalDate hoje = LocalDate.now();
        inserir("APROVADA", "5000", hoje, true);
        inserir("APROVADA", "15000", hoje, true);
        inserir("REJEITADA", "300", hoje, true);
        inserir("PENDENTE", "999", hoje, false);
        inserir("APROVADA", "777", hoje.minusDays(1), true);
    }

    private void inserir(String status, String valor, LocalDate dia, boolean decidida) {
        Timestamp decisao = decidida ? Timestamp.valueOf(dia.atTime(12, 0)) : null;
        solicitacaoJdbcTemplate.update(
                "INSERT INTO solicitacao (id, cliente_id, valor, status, data_criacao, data_decisao)"
                        + " VALUES (?, ?, ?, ?, now(), ?)",
                UUID.randomUUID(),
                "cliente-fixture",
                new BigDecimal(valor),
                status,
                decisao);
    }

    @Test
    void jobGeraCsvEJsonERerunNaoDuplica() throws Exception {
        String hoje = LocalDate.now().toString();

        JobExecution primeira = disparar(hoje);
        assertEquals(ExitStatus.COMPLETED, primeira.getExitStatus());

        List<String> csv = Files.readAllLines(Path.of(diretorio, "relatorio-" + hoje + ".csv"));
        assertEquals("id,status,valor,data_decisao,alcada", csv.get(0));
        assertEquals(4, csv.size());
        assertTrue(csv.stream().anyMatch(l -> l.contains("15000") && l.contains("SOMENTE_GERENTE")));
        assertEquals(
                2, csv.stream().filter(l -> l.contains("ANALISTA_OU_GERENTE")).count());

        JsonNode resumo = objectMapper.readTree(Files.readString(Path.of(diretorio, "resumo-" + hoje + ".json")));
        assertEquals(hoje, resumo.get("data").asText());
        assertEquals(2, resumo.get("aprovadas").asInt());
        assertEquals(1, resumo.get("rejeitadas").asInt());
        assertEquals(
                0,
                new BigDecimal("20000")
                        .compareTo(resumo.get("valorTotalAprovado").decimalValue()));

        JobExecution segunda = disparar(hoje);
        assertEquals(ExitStatus.COMPLETED, segunda.getExitStatus());

        List<String> csv2 = Files.readAllLines(Path.of(diretorio, "relatorio-" + hoje + ".csv"));
        assertEquals(4, csv2.size());
    }

    private JobExecution disparar(String hoje) throws Exception {
        return jobLauncher.run(
                relatorioDiarioJob,
                new JobParametersBuilder()
                        .addString("data", hoje)
                        .addLong("timestamp", System.currentTimeMillis())
                        .toJobParameters());
    }
}

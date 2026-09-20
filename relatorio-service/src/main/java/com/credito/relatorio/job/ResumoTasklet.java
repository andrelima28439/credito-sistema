package com.credito.relatorio.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class ResumoTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(ResumoTasklet.class);

    private final JdbcTemplate solicitacaoJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String diretorio;
    private final String data;

    public ResumoTasklet(
            JdbcTemplate solicitacaoJdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${app.relatorio.diretorio}") String diretorio,
            @Value("#{jobParameters['data']}") String data) {
        this.solicitacaoJdbcTemplate = solicitacaoJdbcTemplate;
        this.objectMapper = objectMapper;
        this.diretorio = diretorio;
        this.data = data;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<Map<String, Object>> agregados = solicitacaoJdbcTemplate.queryForList(
                """
            SELECT status, COUNT(*) AS total, COALESCE(SUM(valor), 0) AS valor_total
              FROM solicitacao
             WHERE status IN ('APROVADA', 'REJEITADA')
               AND CAST(data_decisao AS DATE) = CAST(? AS DATE)
             GROUP BY status
            """,
                data);

        long aprovadas = 0;
        long rejeitadas = 0;
        BigDecimal valorTotalAprovado = BigDecimal.ZERO;
        for (Map<String, Object> linha : agregados) {
            if ("APROVADA".equals(linha.get("status"))) {
                aprovadas = ((Number) linha.get("total")).longValue();
                valorTotalAprovado = new BigDecimal(String.valueOf(linha.get("valor_total")));
            } else if ("REJEITADA".equals(linha.get("status"))) {
                rejeitadas = ((Number) linha.get("total")).longValue();
            }
        }

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("data", data);
        resumo.put("aprovadas", aprovadas);
        resumo.put("rejeitadas", rejeitadas);
        resumo.put("valorTotalAprovado", valorTotalAprovado);

        Path arquivo = Path.of(diretorio, "resumo-" + data + ".json");
        Files.writeString(
                arquivo,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resumo),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        log.info("resumo do dia {} gravado em {}", data, arquivo);
        return RepeatStatus.FINISHED;
    }
}

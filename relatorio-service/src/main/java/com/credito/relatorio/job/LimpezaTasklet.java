package com.credito.relatorio.job;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class LimpezaTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(LimpezaTasklet.class);

    private final String diretorio;
    private final String data;

    public LimpezaTasklet(
            @Value("${app.relatorio.diretorio}") String diretorio, @Value("#{jobParameters['data']}") String data) {
        this.diretorio = diretorio;
        this.data = data;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Files.createDirectories(Path.of(diretorio));
        boolean csv = Files.deleteIfExists(Path.of(diretorio, "relatorio-" + data + ".csv"));
        boolean json = Files.deleteIfExists(Path.of(diretorio, "resumo-" + data + ".json"));
        log.info("arquivos do dia {} removidos (csv={}, json={})", data, csv, json);
        return RepeatStatus.FINISHED;
    }
}

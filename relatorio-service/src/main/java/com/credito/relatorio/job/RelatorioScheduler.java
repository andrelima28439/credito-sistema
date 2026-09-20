package com.credito.relatorio.job;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RelatorioScheduler {

    private static final Logger log = LoggerFactory.getLogger(RelatorioScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job relatorioDiarioJob;

    public RelatorioScheduler(JobLauncher jobLauncher, Job relatorioDiarioJob) {
        this.jobLauncher = jobLauncher;
        this.relatorioDiarioJob = relatorioDiarioJob;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void gerarRelatorioMeiaNoite() throws Exception {
        String hoje = LocalDate.now().toString();
        log.info("agendamento disparado: relatorio do dia {}", hoje);
        jobLauncher.run(
                relatorioDiarioJob,
                new JobParametersBuilder()
                        .addString("data", hoje)
                        .addLong("timestamp", System.currentTimeMillis())
                        .toJobParameters());
    }
}

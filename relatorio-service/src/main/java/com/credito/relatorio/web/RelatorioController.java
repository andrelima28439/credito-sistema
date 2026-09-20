package com.credito.relatorio.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/relatorios")
public class RelatorioController {

    private final JobLauncher jobLauncher;
    private final Job relatorioDiarioJob;

    public RelatorioController(JobLauncher jobLauncher, Job relatorioDiarioJob) {
        this.jobLauncher = jobLauncher;
        this.relatorioDiarioJob = relatorioDiarioJob;
    }

    @PostMapping("/diario")
    public Map<String, Object> disparar(@RequestParam(required = false) String data) throws Exception {
        String dia = data == null ? LocalDate.now().toString() : validar(data);
        long executionId = jobLauncher
                .run(
                        relatorioDiarioJob,
                        new JobParametersBuilder()
                                .addString("data", dia)
                                .addLong("timestamp", System.currentTimeMillis())
                                .toJobParameters())
                .getId();
        return Map.of("data", dia, "executionId", executionId, "status", "CONCLUIDO");
    }

    private String validar(String data) {
        try {
            return LocalDate.parse(data).toString();
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "data deve ser yyyy-MM-dd");
        }
    }
}

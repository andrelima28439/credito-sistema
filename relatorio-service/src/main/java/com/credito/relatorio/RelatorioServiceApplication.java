package com.credito.relatorio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RelatorioServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RelatorioServiceApplication.class, args);
    }
}

package com.credito.relatorio.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RelatorioLinha(UUID id, String status, BigDecimal valor, Instant dataDecisao, String alcada) {}

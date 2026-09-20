package com.credito.notificacao.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SolicitacaoDecididaEvent(
        UUID id, String status, BigDecimal valor, Instant timestamp, String correlationId) {}

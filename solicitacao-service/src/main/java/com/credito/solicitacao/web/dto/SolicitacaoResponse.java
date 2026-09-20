package com.credito.solicitacao.web.dto;

import com.credito.solicitacao.domain.Solicitacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Solicitacao de credito")
public record SolicitacaoResponse(
        @Schema(example = "17f0ce10-cbd9-4f7c-955d-a2f874dda290") UUID id,
        @Schema(description = "Subject (sub) do JWT do cliente", example = "19cb4577-035c-4acd-990c-fd5a33ea7b5c")
                String clienteId,
        @Schema(example = "5000.00") BigDecimal valor,
        @Schema(example = "PENDENTE") String status,
        Instant dataCriacao,
        Instant dataDecisao,
        String aprovadorId) {

    public static SolicitacaoResponse from(Solicitacao s) {
        return new SolicitacaoResponse(
                s.getId(),
                s.getClienteId(),
                s.getValor(),
                s.getStatus().name(),
                s.getDataCriacao(),
                s.getDataDecisao(),
                s.getAprovadorId());
    }
}

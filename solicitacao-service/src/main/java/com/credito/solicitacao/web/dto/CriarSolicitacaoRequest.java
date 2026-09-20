package com.credito.solicitacao.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Pedido de criacao de solicitacao de credito")
public record CriarSolicitacaoRequest(
        @Schema(description = "Valor solicitado em reais", example = "5000.00") @NotNull @Positive BigDecimal valor) {}

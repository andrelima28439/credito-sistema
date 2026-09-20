package com.credito.solicitacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "solicitacao")
public class Solicitacao {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String clienteId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSolicitacao status;

    @Column(nullable = false)
    private Instant dataCriacao;

    private Instant dataDecisao;

    private String aprovadorId;

    protected Solicitacao() {}

    public Solicitacao(String clienteId, BigDecimal valor) {
        this.clienteId = clienteId;
        this.valor = valor;
        this.status = StatusSolicitacao.PENDENTE;
        this.dataCriacao = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public StatusSolicitacao getStatus() {
        return status;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public Instant getDataDecisao() {
        return dataDecisao;
    }

    public String getAprovadorId() {
        return aprovadorId;
    }

    public void decidir(StatusSolicitacao novoStatus, String aprovadorId) {
        this.status = novoStatus;
        this.aprovadorId = aprovadorId;
        this.dataDecisao = Instant.now();
    }
}

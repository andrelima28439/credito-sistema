package com.credito.notificacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notificacao")
public class Notificacao {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private Instant dataEnvio;

    protected Notificacao() {}

    public Notificacao(UUID id, String status, BigDecimal valor, Instant dataEnvio) {
        this.id = id;
        this.status = status;
        this.valor = valor;
        this.dataEnvio = dataEnvio;
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Instant getDataEnvio() {
        return dataEnvio;
    }
}

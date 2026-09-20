package com.credito.notificacao.rabbit;

public final class RabbitTopology {

    public static final String EXCHANGE = "notificacoes.exchange";
    public static final String ROUTING_KEY = "solicitacao.decidida";
    public static final String FILA_NOTIFICACOES = "fila.notificacoes";

    public static final String DLX = "notificacoes.dlx";
    public static final String FILA_DLQ = "fila.notificacoes.dlq";

    private RabbitTopology() {}
}

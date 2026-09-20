package com.credito.notificacao.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    DirectExchange notificacoesExchange() {
        return new DirectExchange(RabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange notificacoesDlx() {
        return new DirectExchange(RabbitTopology.DLX, true, false);
    }

    @Bean
    Queue filaNotificacoes() {
        return QueueBuilder.durable(RabbitTopology.FILA_NOTIFICACOES)
                .deadLetterExchange(RabbitTopology.DLX)
                .deadLetterRoutingKey(RabbitTopology.ROUTING_KEY)
                .build();
    }

    @Bean
    Queue filaNotificacoesDlq() {
        return QueueBuilder.durable(RabbitTopology.FILA_DLQ).build();
    }

    @Bean
    Binding bindingNotificacoes(DirectExchange notificacoesExchange, Queue filaNotificacoes) {
        return BindingBuilder.bind(filaNotificacoes).to(notificacoesExchange).with(RabbitTopology.ROUTING_KEY);
    }

    @Bean
    Binding bindingDlq(DirectExchange notificacoesDlx, Queue filaNotificacoesDlq) {
        return BindingBuilder.bind(filaNotificacoesDlq).to(notificacoesDlx).with(RabbitTopology.ROUTING_KEY);
    }
}

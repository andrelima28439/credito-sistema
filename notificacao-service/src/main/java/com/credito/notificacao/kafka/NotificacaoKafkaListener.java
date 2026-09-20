package com.credito.notificacao.kafka;

import com.credito.notificacao.rabbit.RabbitTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificacaoKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoKafkaListener.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificacaoKafkaListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.solicitacao-decidida:solicitacao.decidida}",
            groupId = "${spring.kafka.consumer.group-id:notificacao-service}")
    public void aoDecidir(SolicitacaoDecididaEvent evento) {
        if (evento == null) {
            log.warn("evento kafka invalido (desserializacao falhou), ignorado");
            return;
        }
        if (evento.correlationId() != null) {
            MDC.put("correlation-id", evento.correlationId());
        }
        try {
            log.info("evento recebido do kafka evento_id={} status={}", evento.id(), evento.status());
            rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY, evento);
        } finally {
            MDC.clear();
        }
    }
}

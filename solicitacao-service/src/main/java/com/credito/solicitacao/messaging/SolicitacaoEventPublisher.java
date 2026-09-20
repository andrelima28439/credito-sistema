package com.credito.solicitacao.messaging;

import com.credito.solicitacao.domain.Solicitacao;
import com.credito.solicitacao.web.CorrelationIdFilter;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SolicitacaoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SolicitacaoEventPublisher.class);

    private final KafkaTemplate<String, SolicitacaoDecididaEvent> kafka;
    private final String topic;

    public SolicitacaoEventPublisher(
            KafkaTemplate<String, SolicitacaoDecididaEvent> kafka,
            @Value("${app.kafka.topic.solicitacao-decidida:solicitacao.decidida}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void publicar(Solicitacao solicitacao) {
        SolicitacaoDecididaEvent event = new SolicitacaoDecididaEvent(
                solicitacao.getId(),
                solicitacao.getStatus().name(),
                solicitacao.getValor(),
                Instant.now(),
                MDC.get(CorrelationIdFilter.MDC_KEY));
        kafka.send(topic, solicitacao.getId().toString(), event);
        log.info("evento_publicado topico={} evento_id={}", topic, solicitacao.getId());
    }
}

package com.credito.notificacao.rabbit;

import com.credito.notificacao.domain.Notificacao;
import com.credito.notificacao.kafka.SolicitacaoDecididaEvent;
import com.credito.notificacao.repository.NotificacaoRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificacaoRabbitListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoRabbitListener.class);

    private final NotificacaoRepository repository;
    private final NotificacaoSender sender;

    public NotificacaoRabbitListener(NotificacaoRepository repository, NotificacaoSender sender) {
        this.repository = repository;
        this.sender = sender;
    }

    @RabbitListener(queues = RabbitTopology.FILA_NOTIFICACOES)
    @Transactional
    public void receber(SolicitacaoDecididaEvent evento) {
        if (evento == null || evento.id() == null) {
            throw new IllegalArgumentException("evento de notificacao sem id");
        }
        if (evento.correlationId() != null) {
            MDC.put("correlation-id", evento.correlationId());
        }
        try {
            if (repository.existsById(evento.id())) {
                log.info("notificacao_duplicada_ignorada evento_id={}", evento.id());
                return;
            }
            sender.enviar(evento);
            repository.save(new Notificacao(evento.id(), evento.status(), evento.valor(), Instant.now()));
        } finally {
            MDC.clear();
        }
    }
}

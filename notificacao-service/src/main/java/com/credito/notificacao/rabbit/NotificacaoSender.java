package com.credito.notificacao.rabbit;

import com.credito.notificacao.kafka.SolicitacaoDecididaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificacaoSender {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoSender.class);

    public void enviar(SolicitacaoDecididaEvent evento) {
        log.info("notificacao_enviada evento_id={} status={} valor={}", evento.id(), evento.status(), evento.valor());

        // Producao: trocar este log por um provedor real (ex: SendGrid):
        // SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
        // Mail mail = new Mail(
        //     new Email("nao-responda@credito-sistema.local"),
        //     "Sua solicitacao " + evento.id() + " foi " + evento.status(),
        //     new Email(clienteEmail), new Content("text/plain", "..."));
        // sg.api(new Request(Method.POST, "mail/send", mail.build()));
    }
}

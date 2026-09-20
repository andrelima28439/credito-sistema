package com.credito.notificacao;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.credito.notificacao.kafka.SolicitacaoDecididaEvent;
import com.credito.notificacao.rabbit.NotificacaoRabbitListener;
import com.credito.notificacao.rabbit.RabbitTopology;
import com.credito.notificacao.repository.NotificacaoRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@Testcontainers
class NotificacaoFluxoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Value("${app.kafka.topic.solicitacao-decidida}")
    String topico;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    NotificacaoRepository repository;

    @org.springframework.boot.test.mock.mockito.SpyBean
    NotificacaoRabbitListener listenerSpy;

    @Test
    void fluxoKafkaParaRabbitGeraNotificacao() {
        UUID id = UUID.randomUUID();
        kafkaTemplate.send(
                topico,
                id.toString(),
                new SolicitacaoDecididaEvent(id, "APROVADA", new BigDecimal("5000"), Instant.now(), null));

        await().atMost(60, TimeUnit.SECONDS).until(() -> repository.findById(id).isPresent());

        assertEquals("APROVADA", repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void mensagemDuplicadaGeraUmaUnicaNotificacao() {
        UUID id = UUID.randomUUID();
        SolicitacaoDecididaEvent evento =
                new SolicitacaoDecididaEvent(id, "REJEITADA", new BigDecimal("300"), Instant.now(), null);
        kafkaTemplate.send(topico, id.toString(), evento);
        kafkaTemplate.send(topico, id.toString(), evento);

        await().pollDelay(Duration.ofSeconds(4))
                .atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(
                        1L,
                        repository.findAll().stream()
                                .filter(n -> n.getId().equals(id))
                                .count()));
    }

    @Test
    void mensagemKafkaMalformadaNaoTravaConsumer() {
        String chave = UUID.randomUUID().toString();
        kafkaTemplate.send(topico, chave, "isto-nao-eh-um-evento-valido {{{");

        UUID id = UUID.randomUUID();
        kafkaTemplate.send(
                topico,
                chave,
                new SolicitacaoDecididaEvent(id, "APROVADA", new BigDecimal("700"), Instant.now(), null));

        await().atMost(60, TimeUnit.SECONDS).until(() -> repository.findById(id).isPresent());

        assertEquals("APROVADA", repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void mensagemComFalhaVaiParaDlqAposTentativas() {
        Map<String, Object> veneno = new HashMap<>();
        veneno.put("id", null);
        veneno.put("status", "APROVADA");
        veneno.put("valor", new BigDecimal("100"));
        veneno.put("timestamp", Instant.now().toString());
        rabbitTemplate.convertAndSend(RabbitTopology.FILA_NOTIFICACOES, veneno);

        Message dlq = await().atMost(60, TimeUnit.SECONDS)
                .until(
                        () -> rabbitTemplate.receive(RabbitTopology.FILA_DLQ, 1000),
                        org.hamcrest.Matchers.notNullValue());
        assertNotNull(dlq);

        List<Map<String, Object>> xDeath =
                (List<Map<String, Object>>) dlq.getMessageProperties().getHeader("x-death");
        assertNotNull(xDeath);
        assertEquals("rejected", String.valueOf(xDeath.get(0).get("reason")));

        verify(listenerSpy, times(3)).receber(any(SolicitacaoDecididaEvent.class));
    }
}

package com.credito.solicitacao.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SolicitacaoControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void clienteCriaSolicitacao() throws Exception {
        mockMvc.perform(post("/solicitacoes")
                        .with(cliente("cliente-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value("cliente-a"))
                .andExpect(jsonPath("$.valor").value(5000))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void valorZeroOuNegativoRetorna400() throws Exception {
        mockMvc.perform(post("/solicitacoes")
                        .with(cliente("cliente-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/solicitacoes")
                        .with(cliente("cliente-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": -100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteVeSoAsProprias() throws Exception {
        String idA = criarComo("cliente-a", "100");
        String idB = criarComo("cliente-b", "200");

        MvcResult result = mockMvc.perform(get("/solicitacoes").with(cliente("cliente-a")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(lista.size() >= 1);
        for (JsonNode item : lista) {
            assertEquals("cliente-a", item.get("clienteId").asText());
        }

        mockMvc.perform(get("/solicitacoes/" + idB).with(cliente("cliente-a"))).andExpect(status().isForbidden());

        mockMvc.perform(get("/solicitacoes/" + idA).with(cliente("cliente-a"))).andExpect(status().isOk());
    }

    @Test
    void clienteNaoPodeVerNemDecidirSolicitacaoDeOutro() throws Exception {
        String idB = criarComo("cliente-b", "100");

        mockMvc.perform(get("/solicitacoes/" + idB).with(cliente("cliente-a"))).andExpect(status().isForbidden());

        mockMvc.perform(patch("/solicitacoes/" + idB + "/aprovar").with(cliente("cliente-a")))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/solicitacoes/" + idB + "/rejeitar").with(cliente("cliente-a")))
                .andExpect(status().isForbidden());
    }

    @Test
    void analistaAprovaValorBaixoEPublicaEvento() throws Exception {
        String id = criarComo("cliente-a", "5000");

        try (KafkaConsumer<String, String> consumer = criarConsumidor()) {
            consumer.subscribe(Collections.singletonList("solicitacao.decidida"));
            consumer.poll(Duration.ofSeconds(2));

            mockMvc.perform(patch("/solicitacoes/" + id + "/aprovar").with(analista("ana-1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APROVADA"))
                    .andExpect(jsonPath("$.aprovadorId").value("ana-1"));

            JsonNode evento = aguardarEvento(consumer, id);
            assertEquals("APROVADA", evento.get("status").asText());
            assertEquals(
                    0,
                    new BigDecimal("5000.00")
                            .compareTo(new BigDecimal(evento.get("valor").asText())));
        }
    }

    @Test
    void analistaNaoPodeAprovarValorAlto() throws Exception {
        String id = criarComo("cliente-a", "15000");

        mockMvc.perform(patch("/solicitacoes/" + id + "/aprovar").with(analista("ana-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteAprovaValorAlto() throws Exception {
        String id = criarComo("cliente-a", "15000");

        try (KafkaConsumer<String, String> consumer = criarConsumidor()) {
            consumer.subscribe(Collections.singletonList("solicitacao.decidida"));
            consumer.poll(Duration.ofSeconds(2));

            mockMvc.perform(patch("/solicitacoes/" + id + "/aprovar").with(gerente("ger-1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APROVADA"));

            aguardarEvento(consumer, id);
        }
    }

    @Test
    void clienteNaoPodeAprovar() throws Exception {
        String id = criarComo("cliente-a", "100");

        mockMvc.perform(patch("/solicitacoes/" + id + "/aprovar").with(cliente("cliente-a")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejeitarMudaStatusEPublicaEvento() throws Exception {
        String id = criarComo("cliente-a", "300");

        try (KafkaConsumer<String, String> consumer = criarConsumidor()) {
            consumer.subscribe(Collections.singletonList("solicitacao.decidida"));
            consumer.poll(Duration.ofSeconds(2));

            mockMvc.perform(patch("/solicitacoes/" + id + "/rejeitar").with(analista("ana-1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJEITADA"));

            JsonNode evento = aguardarEvento(consumer, id);
            assertEquals("REJEITADA", evento.get("status").asText());
        }
    }

    @Test
    void aprovarSolicitacaoJaDecididaRetorna400() throws Exception {
        String id = criarComo("cliente-a", "300");

        mockMvc.perform(patch("/solicitacoes/" + id + "/aprovar").with(analista("ana-1")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/solicitacoes/" + id + "/aprovar").with(analista("ana-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscarInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/solicitacoes/" + UUID.randomUUID()).with(gerente("ger-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorHealthEMetricsPublicos() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
    }

    @Test
    void correlationIdPropagadoParaEvento() throws Exception {
        MvcResult criado = mockMvc.perform(post("/solicitacoes")
                        .with(cliente("cliente-a"))
                        .header("X-Correlation-Id", "corr-teste-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 100}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "corr-teste-123"))
                .andReturn();
        String id = objectMapper
                .readTree(criado.getResponse().getContentAsString())
                .get("id")
                .asText();

        try (KafkaConsumer<String, String> consumer = criarConsumidor()) {
            consumer.subscribe(Collections.singletonList("solicitacao.decidida"));
            consumer.poll(Duration.ofSeconds(2));

            mockMvc.perform(patch("/solicitacoes/" + id + "/aprovar")
                            .with(analista("ana-1"))
                            .header("X-Correlation-Id", "corr-teste-123"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Correlation-Id", "corr-teste-123"));

            JsonNode evento = aguardarEvento(consumer, id);
            assertEquals("corr-teste-123", evento.get("correlationId").asText());
        }
    }

    private String criarComo(String subject, String valor) throws Exception {
        MvcResult result = mockMvc.perform(post("/solicitacoes")
                        .with(cliente(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": " + valor + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private KafkaConsumer<String, String> criarConsumidor() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "teste-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private JsonNode aguardarEvento(KafkaConsumer<String, String> consumer, String id) throws Exception {
        long fim = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < fim) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                JsonNode evento = objectMapper.readTree(record.value());
                if (id.equals(evento.get("id").asText())) {
                    return evento;
                }
            }
        }
        throw new AssertionError("evento nao recebido para id " + id);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .JwtRequestPostProcessor
            cliente(String subject) {
        return jwt().jwt(j -> j.subject(subject).claim("preferred_username", subject))
                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .JwtRequestPostProcessor
            analista(String subject) {
        return jwt().jwt(j -> j.subject(subject).claim("preferred_username", subject))
                .authorities(new SimpleGrantedAuthority("ROLE_ANALISTA"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .JwtRequestPostProcessor
            gerente(String subject) {
        return jwt().jwt(j -> j.subject(subject).claim("preferred_username", subject))
                .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"));
    }
}

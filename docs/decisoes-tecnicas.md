# Decisões Técnicas (ADRs)

Registro das decisões de arquitetura e seus motivos.

## Infraestrutura

### ADR-001: 1 container Postgres com 3 databases
- **Contexto:** duas opções avaliadas: "um banco por serviço, ou schemas separados".
- **Decisão:** 1 container `postgres:16-alpine`, 3 databases (`solicitacao_db`, `notificacao_db`, `relatorio_db`) criados via script `docker/postgres-init.sh` + var `POSTGRES_MULTIPLE_DATABASES`.
- **Motivo:** decisão mais simples e comum em ambiente local/dev Spring Boot; economiza RAM vs 3 containers; mantém isolamento lógico por database; fácil migrar para 1 banco por serviço em produção (só mudar JDBC URL). Volume nomeado `credito-pgdata`.

### ADR-002: Kafka em modo KRaft (sem Zookeeper)
- **Contexto:** duas opções avaliadas: "Kafka + Zookeeper (ou KRaft, mais moderno)".
- **Decisão:** imagem `apache/kafka:3.7.0`, `KAFKA_PROCESS_ROLES=broker,controller`, single-node, `CLUSTER_ID` fixo `MkU3OEVBNTcwNTJENDM2Qk`.
- **Motivo:** KRaft é o padrão atual (Zookeeper removido no Kafka 4.0); imagem oficial Apache; single-node suficiente para ambiente local. Listeners: `kafka:29092` (interno) + `localhost:9092` (externo).

### ADR-003: UI do Kafka = Kafdrop
- **Contexto:** duas opções avaliadas: "Redpanda Console ou Kafdrop".
- **Decisão:** `obsidiandynamics/kafdrop:4.1.0` na porta `9000`, `KAFKA_BROKERCONNECT=kafka:29092`.
- **Motivo:** imagem mais leve que Redpanda Console; suficiente para visualizar tópico `solicitacao.decidida` em demo.

### ADR-004: RabbitMQ com management
- **Contexto:** requisito de painel na `15672`.
- **Decisão:** `rabbitmq:3-management-alpine`, portas `5672` (AMQP) + `15672` (painel), volume `rabbitdata`.
- **Motivo:** tag `-management` é o padrão para ter UI sem config extra.

### ADR-005: Keycloak 24.0.5 em modo dev com import de realm
- **Contexto:** requisito de import automático de `realm-export.json` no boot.
- **Decisão:** `quay.io/keycloak/keycloak:24.0.5`, comando `start-dev --import-realm --health-enabled=true`, mount `./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro`, porta `8080`, H2 em arquivo efêmero (sem volume nomeado).
- **Motivo:** modo dev dispensa TLS/DB externo; `--import-realm` é a forma padrão de subir já pronto; H2 local é aceitável para desenvolvimento local (documentado como limitação p/ produção nas Limitações conhecidas do README). Tentativa inicial de persistir `/opt/keycloak/data/h2` em volume nomeado falhou (imagem roda como non-root, erro `Could not save properties ...keycloakdb.lock.db`); removido o volume — Postgres/Kafka/Rabbit seguem com volumes persistentes.
- **Healthcheck:** imagem não tem `curl`/`wget` (`sh: curl: command not found`); usa `exec 3<>/dev/tcp/localhost/8080` + `GET /health/ready` + `grep -q 'UP'` (testado via `docker exec`, retorna `HEALTHY`).

### ADR-006: Portas locais
- **Decisão:** Postgres `5432`, Keycloak `8080`, Kafka `9092`, RabbitMQ `5672`/`15672`, Kafdrop `9000`.
- **Motivo:** portas padrão de cada tecnologia (convenção Spring Boot/ecossistema); evita mapeamento confuso na demo e na collection Postman.

### ADR-007: Healthchecks + `depends_on: condition: service_healthy`
- **Decisão:** `pg_isready` (postgres), `kafka-topics.sh --list` (kafka), `rabbitmq-diagnostics ping` (rabbit), `GET /health/ready` (keycloak via /dev/tcp), `wget /` (kafdrop).
- **Motivo:** evita startup fora de ordem (tudo "healthy" sem intervenção manual).

## Keycloak

### ADR-008: Roles como realm roles (`CLIENTE`, `ANALISTA`, `GERENTE`)
- **Contexto:** requisito de 3 papéis visíveis no JWT em `realm_access.roles`.
- **Decisão:** realm roles (não client roles).
- **Motivo:** RBAC mais simples com Spring Security (`jwt.getClaimAsStringList("realm_access.roles")` ou conversor padrão); visível em qualquer client do realm; é o padrão mais comum em tutoriais Spring + Keycloak.

### ADR-009: Client `solicitacao-service` confidential com 3 flows
- **Contexto:** requisito de Authorization Code + Client Credentials.
- **Decisão:** `standardFlowEnabled=true`, `serviceAccountsEnabled=true`, `publicClient=false`, `clientAuthenticatorType=client-secret` + `directAccessGrantsEnabled=true` (extra, só p/ testes locais/Postman via password grant).
- **Motivo:** Auth Code é o fluxo real (Swagger + futuros frontends); Client Credentials serve a integrações máquina-máquina; Direct Access permite `curl`/Postman sem browser na demo — documentado no README como atalho de teste, em produção usaria Auth Code.

### ADR-010: Secret fixo de dev `solicitacao-secret-dev-123`
- **Contexto:** nome/valor do secret em aberto (decisão livre).
- **Decisão:** secret fixo e documentado no README.
- **Motivo:** reprodutibilidade da demo e da collection Postman; valor só vale para ambiente local (H2 efêmero); em produção o secret viria de vault/Secret K8s, nunca commitado.

### ADR-011: Usuários `cliente1`/`analista1`/`gerente1` com senha `senha123`
- **Contexto:** requisito de "senha simples documentada".
- **Decisão:** padrão `<role>1` / `senha123`, criados via Admin API e persistidos no `realm-export.json` com `credentials: [{type: password, value}]` (Keycloak faz hash no import).
- **Motivo:** convenção simples e memorável para demo e Postman.

### ADR-012: Roles no JWT via client scope padrão `roles` (sem mapper custom)
- **Contexto:** requisito de "garantir que a role apareça no JWT".
- **Decisão:** nenhum mapper custom; usa o mapper built-in do scope `roles` que expõe `realm_access.roles`.
- **Motivo:** menos configuração para manter; verificado decodificando o JWT (`realm_access.roles` contém `CLIENTE`/`ANALISTA`/`GERENTE`).

### ADR-013: Construção do export via Admin API + `partial-export` + `users` manuais
- **Contexto:** `partial-export?exportClients=true&exportGroupsAndRoles=true` não inclui usuários regulares (só service-account).
- **Decisão:** export parcial como base + bloco `users` com os 3 testes + secret fixo; reimport validado com `docker compose up -d --force-recreate keycloak` (H2 efêmero garante teste "do zero").
- **Motivo:** garante `realm-export.json` completo e reimportável sem UI. Armadilha encontrada: PowerShell 5.1 serializa array de 1 elemento como objeto (`ConvertTo-Json` sem `-AsArray`); role-mapping exige array (`[{"id":...,"name":...}]`), então o JSON foi montado como string `"[" + obj + "]"`.

## solicitacao-service

### ADR-014: Java 21 + Spring Boot 3.2.5 + Maven multi-módulo
- **Contexto:** build tool em aberto (Maven ou Gradle); Java 21 (LTS) como versão alvo.
- **Decisão:** `pom.xml` pai (`spring-boot-starter-parent` 3.2.5, `java.version=21`, packaging `pom`) + módulo `solicitacao-service`; sem Lombok (código explícito, sem annotation processing).
- **Motivo:** decisão mais comum no ecossistema; Boot 3.x exige Java 17+, 21 é LTS atual; parent-first garante versões consistentes nos próximos serviços.

### ADR-015: Porta 8081
- **Contexto:** Keycloak ocupa 8080.
- **Decisão:** `server.port=8081` (já previsto nos `redirectUris` do client Keycloak).
- **Motivo:** convenção incremental simples; evita conflito local.

### ADR-016: Flyway + 1 database (`solicitacao_db`)
- **Contexto:** requisito de migrations versionadas (Flyway ou Liquibase), sem `ddl-auto: update` como estratégia final.
- **Decisão:** Flyway com `V1__create_solicitacao.sql` (tabela + índices + `CHECK (valor > 0)`); `ddl-auto: validate`.
- **Motivo:** Flyway com SQL puro é o mais simples para um domínio pequeno; CHECK no banco como segunda barreira além do `@Positive`.

### ADR-017: `clienteId`/`aprovadorId` = JWT `sub`
- **Contexto:** origem do id do cliente em aberto (decisão livre).
- **Decisão:** extraído do `Jwt.getSubject()` no controller; nunca aceito do body (body só tem `valor`).
- **Motivo:** impede forjar identidade; padrão em resource servers.

### ADR-018: Segurança — `realm_access.roles` → `ROLE_*` + `@PreAuthorize`
- **Decisão:** `JwtAuthenticationConverter` custom mapeia cada role do realm para `ROLE_<NOME>`; endpoints usam `hasRole('CLIENTE')` / `hasAnyRole('ANALISTA','GERENTE')`; regra de alçada (valor x role) fica em `RegraAlcada`/`SolicitacaoService` (testável sem Spring) e gera 403 via `AccessDeniedException`.
- **Motivo:** separa autenticação (anotação) de regra de negócio (classe pura com 100% dos casos de borda testados).

### ADR-019: Erros RFC 7807 (`ProblemDetail`)
- **Decisão:** `GlobalExceptionHandler` retorna `ProblemDetail`: 403 falta de permissão (inclui cliente vendo dado de outro), 404 inexistente, 400 validação/`IllegalArgumentException`/`IllegalStateException` (ex: valor ≤ 0, solicitação já decidida).
- **Motivo:** formato consistente (RFC 7807/Problem Details); 400 para "já decidida" (em vez de 409) para ficar nos códigos 400/403/404 do contrato.

### ADR-020: Rejeitar exige a mesma alçada que aprovar
- **Contexto:** quem pode rejeitar em aberto (decisão livre).
- **Decisão:** `rejeitar` usa a mesma `RegraAlcada.podeAprovar` (analista só até 10k).
- **Motivo:** simetria simples; quem não tem alçada para decidir não decide nem para negar.

### ADR-021: Evento `solicitacao.decidida` (JSON)
- **Decisão:** tópico `solicitacao.decidida` (propriedade `app.kafka.topic.solicitacao-decidida`), chave = id, valor JSON do record `SolicitacaoDecididaEvent(id, status, valor, timestamp)`. Exemplo:
  ```json
  {"id":"17f0ce10-cbd9-4f7c-955d-a2f874dda290","status":"APROVADA","valor":500.00,"timestamp":"2026-09-16T05:50:00Z"}
  ```
- **Motivo:** JSON simples como permitido (Avro seria bônus); chave = id preserva ordem por solicitação; publicado após o `save` na mesma transação de decisão.

### ADR-022: Testes — Testcontainers 1.21.3 + `ApacheKafkaContainer` + `@DynamicPropertySource`
- **Decisão:** `RegraAlcadaTest` (12 casos, limite/borda) + `SolicitacaoControllerTest` (10 casos: Postgres `postgres:16-alpine` + Kafka `apache/kafka:3.7.0` reais, JWT mockado com `jwt()` + `ROLE_*`, evento Kafka consumido e validado). Usa `org.testcontainers.kafka.KafkaContainer` (nova API p/ imagem nativa; `KafkaContainer` antiga + `asCompatibleSubstituteFor` quebra com exit 127) e `@DynamicPropertySource` em vez de `@ServiceConnection` (explícito, imune à versão do Boot).
- **Motivo:** satisfaz "Postgres real + Kafka real" e "cliente não vê dado de outro" de verdade.

### ADR-023: Compatibilidade Testcontainers × Docker Engine
- **Problemas encontrados:** (a) o Testcontainers fixa uma versão antiga da API Docker quando `api.version` é desconhecido, e Engines recentes exigem versão mínima maior (`client version ... is too old`); (b) no Windows, a autodetecção do Docker pode escolher um pipe que não responde.
- **Decisão:** `testcontainers.version=1.21.3` no pom pai; `maven-surefire-plugin` com `<api.version>1.44</api.version>` (vale p/ CI Linux também); documentar que no Windows pode ser necessário rodar com `DOCKER_HOST` apontando para o pipe funcional da instalação.
- **Motivo:** `mvn test` precisa passar sem flags manuais; DOCKER_HOST é específico da máquina e fica fora do repo.

## notificacao-service

### ADR-024: Topologia RabbitMQ com DLQ declarada em código
- **Decisão:** exchange `notificacoes.exchange` (direct) + fila `fila.notificacoes` (com `x-dead-letter-exchange=notificacoes.dlx`) + `fila.notificacoes.dlq` ligada à DLX; tudo como `@Bean` (`RabbitTopologyConfig`), declarado automaticamente pelo `RabbitAdmin` no boot — verificado no broker real (`/api/queues` lista as duas filas).
- **Motivo:** DLQ de verdade no broker (mensagem não some), sem try/catch engolindo erro; `fila.notificacoes` como nome de exemplo.

### ADR-025: Retry stateless 3 tentativas + `AmqpRejectAndDontRequeueException`
- **Decisão:** `RetryInterceptorBuilder.stateless().maxAttempts(3).backOffOptions(200, 1.5, 1000)` com recoverer lançando `AmqpRejectAndDontRequeueException` → sem requeue → DLX → DLQ.
- **Motivo:** tentativa inicial de usar `RejectAndDontRequeueRecoverer` direto não compila (ele é `MessageRecoverer`, mas o `recoverer()` do spring-retry atual só aceita `MethodInvocationRecoverer` — verificado por `javap`). Registro honesto: stateless = 3 tentativas in-memory + 1 dead-lettering (`x-death` com `count=1`, `reason=rejected`); as 3 tentativas são provadas no teste via `@SpyBean` (`verify(times(3))`).

### ADR-026: Idempotência via chave primária (`notificacao.id` = id do evento)
- **Decisão:** tabela `notificacao` (Flyway `V1__create_notificacao.sql` em `notificacao_db`); consumer faz `existsById` → skip com log `notificacao_duplicada_ignorada`, senão envia e salva na mesma transação.
- **Motivo:** sobrevive a restart (ao contrário de Set em memória); teste envia o mesmo evento 2x pelo Kafka e assert `count==1`.

### ADR-027: Envio mockado com ponto de extensão documentado
- **Decisão:** `NotificacaoSender.enviar()` faz log estruturado (`notificacao_enviada evento_id=... status=... valor=...`) + bloco comentado mostrando a chamada SendGrid.
- **Motivo:** envio real fora de escopo; o bloco comentado documenta como plugar o provedor real.

### ADR-028: Sem Spring Security neste serviço (por enquanto)
- **Decisão:** sem `oauth2-resource-server`; só actuator. Serviço interno sem endpoints de negócio.
- **Motivo:** simplicidade; registrado como limitação conhecida no README (em produção, restringir actuator ou pôr o serviço fora da rede pública).

### ADR-029: Porta 8082, `notificacao_db`, group `notificacao-service`
- **Decisão:** `server.port=8082`; consumer Kafka `JsonDeserializer` com `spring.json.value.default.type` apontando para o record do evento (sem Serialized headers); `RabbitTemplate` e listener com `Jackson2JsonMessageConverter` usando o `ObjectMapper` do Boot (necessário para `Instant`).
- **Motivo:** convenções incrementais; ObjectMapper do Boot evita falha de (de)serialização de `Instant` que o conversor default teria.

## relatorio-service

### ADR-030: Leitura direta read-only do `solicitacao_db`
- **Contexto:** acesso aos dados em aberto (outro serviço é o dono).
- **Decisão:** dois DataSources no `relatorio-service`: primário `relatorio_db` (metadados do Spring Batch via `spring.batch.jdbc.initialize-schema=always`, sem Flyway) + secundário `app.datasource.solicitacao` apontando para o `solicitacao_db`, usado SOMENTE para leitura (reader paginado + query de agregação). Sem JPA neste serviço (só JDBC).
- **Motivo:** decisão mais simples e comum para reporting; evita replicar pipeline de eventos só para o relatório. Trade-off honesto: acopla no nível de dados (se o schema de `solicitacao` mudar, o job quebra) — em produção o correto seria réplica de leitura ou CDC/eventos; registrado na seção de limitações do README.

### ADR-031: Job em 3 steps com Reader/Processor/Writer separados
- **Decisão:** `limpezaStep` (tasklet apaga os arquivos do dia) → `detalheStep` (chunk 100: `JdbcPagingItemReader` com `PostgresPagingQueryProvider` + `RelatorioProcessor` + `FlatFileItemWriter` CSV) → `resumoStep` (tasklet com `COUNT/SUM GROUP BY status` → `resumo-{data}.json`). Cada papel em classe própria (`SolicitacaoRowMapper`, `RelatorioProcessor`, `LimpezaTasklet`, `ResumoTasklet`, `RelatorioJobConfig` só monta).
- **Motivo:** separação explícita Reader/Processor/Writer; processor com transformação real (coluna `alcada`: `ANALISTA_OU_GERENTE` ≤ 10k, senão `SOMENTE_GERENTE`).

### ADR-032: Idempotência por sobrescrita + parâmetro único por disparo
- **Decisão:** arquivos do dia são APAGADOS no início (`limpezaStep`) e reescritos (`shouldDeleteIfExists`, `TRUNCATE_EXISTING`); cada disparo leva `timestamp` além de `data`, então cada execução é uma instância nova que reescreve tudo. Rerun do mesmo dia ou restart no meio → saída final idêntica, sem duplicatas (não há resume parcial — escolha consciente para um arquivo diário pequeno).
- **Motivo:** testado de verdade (disparo duplo no teste, CSV continua com as mesmas linhas). Tentativa inicial com `RunIdIncrementer` FALHOU do jeito instrutivo: ele deriva `run.id` dos parâmetros de entrada, então dois disparos com `{data}` igual geram `run.id=1` igual → `JobInstanceAlreadyCompleteException` (visto no log do teste). Removido em favor do `timestamp` explícito.

### ADR-033: Agendamento meia-noite + endpoint manual
- **Decisão:** `@Scheduled(cron = "0 0 0 * * *")` + `POST /relatorios/diario?data=yyyy-MM-dd` (default hoje; data inválida → 400). `spring.batch.job.enabled=false` para o job não rodar sozinho no boot.
- **Motivo:** os dois (agendamento + disparo manual), sem precisar esperar a meia-noite.

### ADR-034: Porta 8083, saída em `./relatorios`, sem Security
- **Decisão:** `server.port=8083`; diretório configurável (`app.relatorio.diretorio`, no teste um temp dir); sem auth (mesmo motivo do notificacao-service — serviço interno, limitação a registrar).
- **Motivo:** convenções incrementais.

## Observabilidade

### ADR-035: Logs JSON com `logstash-logback-encoder` 7.4
- **Decisão:** `logback-spring.xml` idêntico nos 3 serviços (Console + `LogstashEncoder`, campo `servico` = `spring.application.name`); MDC `correlation-id` aparece automaticamente em toda linha. Versão **fixada no pom pai** — o BOM do Boot 3.2 NÃO gerencia esse artefato (build quebrou com `version is missing` até o pin).
- **Motivo:** JSON estruturado com `@timestamp`, `level`, `logger_name`, `message`, `correlation-id`, `servico` — formato real verificado nos logs da demo).

### ADR-036: Correlation-id `X-Correlation-Id` → MDC → evento → MDC
- **Decisão:** `CorrelationIdFilter` (solicitacao-service) lê o header ou gera UUID, põe no MDC, devolve no response; `SolicitacaoEventPublisher` copia o MDC para o campo novo `correlationId` do evento (nullable = compatível com mensagens antigas); listeners Kafka e Rabbit do notificacao-service restauram o MDC (com `clear()` em `finally`).
- **Motivo:** mesmo id visível em solicitacao → Kafka → notificacao (demo viva com `demo-corr-003` mostra as 5 linhas). Teste automatizado `correlationIdPropagadoParaEvento` (header ecoado + campo no evento). relatorio-service fica fora do caminho (lê banco; só logs JSON).

### ADR-037: Desacoplamento da serialização Kafka entre serviços (bug real pego na demo)
- **Bug:** producer (solicitacao) enviava type headers (`__TypeId__=com.credito.solicitacao...`); consumer (notificacao) tentava carregar ESSA classe → `ClassNotFoundException` + poison-loop infinito de `SerializationException` (os testes não pegaram porque lá producer e consumer usam o mesmo pacote!).
- **Decisão:** producer com `spring.json.add.type.headers=false`; consumer com `spring.json.use.type.headers=false` + `default.type` da própria classe + `ErrorHandlingDeserializer` (record inválido vira `null` → log `warn` e skip, sem travar o partition); listener mantém `throw` só para `id` nulo (9 retries do error handler default e segue).
- **Motivo:** produtor nunca deve impor nome de classe Java ao consumidor — contrato é o JSON (ADR-021).

### ADR-038: Métrica de negócio `solicitacoes.decididas{status}`
- **Decisão:** `meterRegistry.counter("solicitacoes.decididas", "status", ...).increment()` em cada decisão; `/actuator/prometheus` e `/actuator/metrics` liberados no SecurityConfig.
- **Motivo:** root cause do `up=0`: estavam retornando **401** (só health/info estavam permitidos) — descoberto testando do container (`8081:401` vs `8082:200`), não era rede. Teste `actuatorHealthEMetricsPublicos` no MockMvc.

### ADR-039: Prometheus + Grafana mínimos no compose
- **Decisão:** `prom/prometheus:v2.53.0` (scrape de `host.docker.internal:808{1,2,3}/actuator/prometheus` — serviços rodam no host em dev) + `grafana/grafana:11.1.0` com datasource provisionado e 1 dashboard mínimo (`${DS_PROMETHEUS}`, painéis: decididas por status, HTTP/s, heap). Credenciais via `.env.example` (`GRAFANA_ADMIN_*`).
- **Motivo:** escopo mínimo de observabilidade, sem virar projeto paralelo; em produção os serviços seriam containers na mesma rede (sem `host.docker.internal`).

### ADR-040: Notas honestas
- `/actuator/prometheus` retorna **404 no contexto MockMvc** (`PrometheusMetricsExportAutoConfiguration` dá back off nos testes — `management.defaults.metrics.export.enabled is considered false` no relatório de condições); cobertura via teste de health/metrics + verificação viva (scrape real). Se um dia precisar no teste, investigar a condição em vez de forçar.
- relatorio-service participa só com logs JSON e métricas JVM/HTTP (fora do caminho do correlation-id).

## CI/CD

### ADR-041: Pipeline `mvn -B verify` + `spotless:check` no `ubuntu-latest`
- **Decisão:** workflow `ci.yml` (push + PR): `setup-java` Temurin 21 com cache Maven → `mvn -B verify` (build + TODOS os testes, incluindo Testcontainers — Docker nativo no runner) → `mvn -B spotless:check` → upload dos HTMLs JaCoCo como artifact. Maven do próprio runner (sem wrapper).
- **Motivo:** tentativa inicial com Maven Wrapper falhou no CI com `Permission denied` (exit 126): o `mvnw` foi commitado no Windows sem o bit executável. Em vez de brigar com modo de arquivo, optou-se pelo Maven do runner — menos uma peça móvel; `verify` (não só `test`) para incluir packaging + relatório JaCoCo.

### ADR-042: Spotless (palantirJavaFormat) em vez de Checkstyle
- **Decisão:** `spotless-maven-plugin` 2.44.0 com `palantirJavaFormat`; `spotless:apply` roda local, `spotless:check` no CI.
- **Motivo:** self-healing (apply garante check verde) vs Checkstyle, que exigiria escrever/suprimir centenas de regras para o estilo existente.

### ADR-043: JaCoCo report sem quality gate
- **Decisão:** `jacoco-maven-plugin` 0.8.11 (`prepare-agent` + `report` no `verify`); cobertura atual ~85–89% de linhas por módulo; sem falhar o build por threshold.
- **Motivo:** gate arbitrário gera fricção sem benefício nesta fase; o relatório existe e é publicado como artifact.

## Collection Postman

### ADR-044: Tokens e ids em dois escopos (collection + environment)
- **Bug pego pelo Newman:** variáveis `token_*`/`solicitacao_id_*` existiam vazias no environment e com valor nas collection variables — o environment tem precedência e **sombreava** os valores preenchidos no login (tudo virou 401).
- **Decisão:** scripts de login/criação gravam nos dois escopos (`collectionVariables` + `environment` com try/catch); environment carrega URLs, credenciais e os slots de token/ids.
- **Motivo:** funciona no GUI com ou sem environment selecionado e no Newman (`-e`); validado com `newman@6`: 8 requests, 14 assertions, 0 falhas (inclui o assert `403` da pasta 5).

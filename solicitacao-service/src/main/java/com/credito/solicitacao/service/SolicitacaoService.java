package com.credito.solicitacao.service;

import com.credito.solicitacao.domain.RegraAlcada;
import com.credito.solicitacao.domain.Solicitacao;
import com.credito.solicitacao.domain.StatusSolicitacao;
import com.credito.solicitacao.messaging.SolicitacaoEventPublisher;
import com.credito.solicitacao.web.RecursoNaoEncontradoException;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolicitacaoService {

    private final com.credito.solicitacao.repository.SolicitacaoRepository repository;
    private final SolicitacaoEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public SolicitacaoService(
            com.credito.solicitacao.repository.SolicitacaoRepository repository,
            SolicitacaoEventPublisher publisher,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Solicitacao criar(BigDecimal valor, String clienteId) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("valor deve ser maior que zero");
        }
        Solicitacao solicitacao = new Solicitacao(clienteId, valor);
        return repository.save(solicitacao);
    }

    @Transactional(readOnly = true)
    public List<Solicitacao> listar(String usuarioId, Set<String> roles, StatusSolicitacao filtro) {
        boolean aprovador = roles.contains("ANALISTA") || roles.contains("GERENTE");
        if (aprovador) {
            if (filtro != null) {
                return repository.findByStatusOrderByDataCriacaoDesc(filtro);
            }
            return repository.findAll();
        }
        return repository.findByClienteIdOrderByDataCriacaoDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public Solicitacao buscarPorId(UUID id, String usuarioId, Set<String> roles) {
        Solicitacao solicitacao = repository
                .findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("solicitacao nao encontrada"));
        boolean aprovador = roles.contains("ANALISTA") || roles.contains("GERENTE");
        if (!aprovador && !solicitacao.getClienteId().equals(usuarioId)) {
            throw new AccessDeniedException("acesso negado");
        }
        return solicitacao;
    }

    @Transactional
    public Solicitacao aprovar(UUID id, String aprovadorId, Set<String> roles) {
        return decidir(id, aprovadorId, roles, StatusSolicitacao.APROVADA);
    }

    @Transactional
    public Solicitacao rejeitar(UUID id, String aprovadorId, Set<String> roles) {
        return decidir(id, aprovadorId, roles, StatusSolicitacao.REJEITADA);
    }

    private Solicitacao decidir(UUID id, String aprovadorId, Set<String> roles, StatusSolicitacao novoStatus) {
        Solicitacao solicitacao = repository
                .findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("solicitacao nao encontrada"));
        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new IllegalStateException("solicitacao ja decidida");
        }
        if (!RegraAlcada.podeAprovar(solicitacao.getValor(), roles)) {
            throw new AccessDeniedException("role sem alcada para este valor");
        }
        solicitacao.decidir(novoStatus, aprovadorId);
        Solicitacao salva = repository.save(solicitacao);
        meterRegistry
                .counter("solicitacoes.decididas", "status", novoStatus.name())
                .increment();
        publisher.publicar(salva);
        return salva;
    }
}

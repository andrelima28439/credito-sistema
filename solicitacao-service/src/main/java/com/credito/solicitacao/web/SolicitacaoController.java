package com.credito.solicitacao.web;

import com.credito.solicitacao.domain.RegraAlcada;
import com.credito.solicitacao.domain.Solicitacao;
import com.credito.solicitacao.domain.StatusSolicitacao;
import com.credito.solicitacao.service.SolicitacaoService;
import com.credito.solicitacao.web.dto.CriarSolicitacaoRequest;
import com.credito.solicitacao.web.dto.SolicitacaoResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/solicitacoes")
public class SolicitacaoController {

    private static final Logger log = LoggerFactory.getLogger(SolicitacaoController.class);

    private final SolicitacaoService service;

    public SolicitacaoController(SolicitacaoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENTE')")
    public SolicitacaoResponse criar(
            @Valid @RequestBody CriarSolicitacaoRequest request, Authentication authentication) {
        String clienteId = subject(authentication);
        Solicitacao salva = service.criar(request.valor(), clienteId);
        log.info("solicitacao_criada id={} valor={}", salva.getId(), salva.getValor());
        return SolicitacaoResponse.from(salva);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SolicitacaoResponse> listar(
            @RequestParam(required = false) StatusSolicitacao status, Authentication authentication) {
        String usuarioId = subject(authentication);
        Set<String> roles = RegraAlcada.extrairRoles(authentication);
        return service.listar(usuarioId, roles, status).stream()
                .map(SolicitacaoResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SolicitacaoResponse detalhar(@PathVariable UUID id, Authentication authentication) {
        String usuarioId = subject(authentication);
        Set<String> roles = RegraAlcada.extrairRoles(authentication);
        return SolicitacaoResponse.from(service.buscarPorId(id, usuarioId, roles));
    }

    @PatchMapping("/{id}/aprovar")
    @PreAuthorize("hasAnyRole('ANALISTA','GERENTE')")
    public SolicitacaoResponse aprovar(@PathVariable UUID id, Authentication authentication) {
        String aprovadorId = subject(authentication);
        Set<String> roles = RegraAlcada.extrairRoles(authentication);
        SolicitacaoResponse resposta = SolicitacaoResponse.from(service.aprovar(id, aprovadorId, roles));
        log.info("solicitacao_decidida id={} status=APROVADA", id);
        return resposta;
    }

    @PatchMapping("/{id}/rejeitar")
    @PreAuthorize("hasAnyRole('ANALISTA','GERENTE')")
    public SolicitacaoResponse rejeitar(@PathVariable UUID id, Authentication authentication) {
        String aprovadorId = subject(authentication);
        Set<String> roles = RegraAlcada.extrairRoles(authentication);
        SolicitacaoResponse resposta = SolicitacaoResponse.from(service.rejeitar(id, aprovadorId, roles));
        log.info("solicitacao_decidida id={} status=REJEITADA", id);
        return resposta;
    }

    private String subject(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return authentication.getName();
    }
}

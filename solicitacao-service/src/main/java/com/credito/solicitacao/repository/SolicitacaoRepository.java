package com.credito.solicitacao.repository;

import com.credito.solicitacao.domain.Solicitacao;
import com.credito.solicitacao.domain.StatusSolicitacao;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, UUID> {

    List<Solicitacao> findByClienteIdOrderByDataCriacaoDesc(String clienteId);

    List<Solicitacao> findByStatusOrderByDataCriacaoDesc(StatusSolicitacao status);
}

package com.credito.notificacao.repository;

import com.credito.notificacao.domain.Notificacao;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {}

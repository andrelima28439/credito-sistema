package com.credito.relatorio.job;

import java.math.BigDecimal;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class RelatorioProcessor implements ItemProcessor<SolicitacaoDecididaRow, RelatorioLinha> {

    static final BigDecimal LIMITE_ANALISTA = new BigDecimal("10000");

    @Override
    public RelatorioLinha process(SolicitacaoDecididaRow item) {
        String alcada = item.valor().compareTo(LIMITE_ANALISTA) <= 0 ? "ANALISTA_OU_GERENTE" : "SOMENTE_GERENTE";
        return new RelatorioLinha(item.id(), item.status(), item.valor(), item.dataDecisao(), alcada);
    }
}

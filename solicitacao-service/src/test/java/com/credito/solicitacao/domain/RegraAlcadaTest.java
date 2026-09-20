package com.credito.solicitacao.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegraAlcadaTest {

    @Test
    void analistaPodeAprovarValorBaixo() {
        assertTrue(RegraAlcada.podeAprovar(new BigDecimal("5000"), Set.of("ANALISTA")));
    }

    @Test
    void gerentePodeAprovarValorBaixo() {
        assertTrue(RegraAlcada.podeAprovar(new BigDecimal("5000"), Set.of("GERENTE")));
    }

    @Test
    void clienteNaoPodeAprovar() {
        assertFalse(RegraAlcada.podeAprovar(new BigDecimal("5000"), Set.of("CLIENTE")));
    }

    @Test
    void analistaNaoPodeAprovarValorAlto() {
        assertFalse(RegraAlcada.podeAprovar(new BigDecimal("15000"), Set.of("ANALISTA")));
    }

    @Test
    void gerentePodeAprovarValorAlto() {
        assertTrue(RegraAlcada.podeAprovar(new BigDecimal("15000"), Set.of("GERENTE")));
    }

    @Test
    void exatamenteNoLimiteAnalistaPode() {
        assertTrue(RegraAlcada.podeAprovar(new BigDecimal("10000"), Set.of("ANALISTA")));
        assertTrue(RegraAlcada.podeAprovar(new BigDecimal("10000"), Set.of("GERENTE")));
    }

    @Test
    void umCentavoAcimaDoLimiteAnalistaNaoPode() {
        assertFalse(RegraAlcada.podeAprovar(new BigDecimal("10000.01"), Set.of("ANALISTA")));
        assertTrue(RegraAlcada.podeAprovar(new BigDecimal("10000.01"), Set.of("GERENTE")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-100.50"})
    void valorZeroOuNegativoLancaExcecao(String valor) {
        assertThrows(
                IllegalArgumentException.class,
                () -> RegraAlcada.podeAprovar(new BigDecimal(valor), Set.of("GERENTE")));
    }

    @Test
    void valorNuloLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> RegraAlcada.podeAprovar(null, Set.of("GERENTE")));
    }

    @Test
    void semRolesNaoPodeAprovar() {
        assertFalse(RegraAlcada.podeAprovar(new BigDecimal("100"), Set.of()));
        assertFalse(RegraAlcada.podeAprovar(new BigDecimal("100"), null));
    }
}

package com.credito.solicitacao.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class RegraAlcada {

    public static final BigDecimal LIMITE_ANALISTA = new BigDecimal("10000");

    private RegraAlcada() {}

    public static boolean podeAprovar(BigDecimal valor, Collection<String> roles) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("valor deve ser maior que zero");
        }
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        if (valor.compareTo(LIMITE_ANALISTA) <= 0) {
            return roles.contains("ANALISTA") || roles.contains("GERENTE");
        }
        return roles.contains("GERENTE");
    }

    public static Set<String> extrairRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        Set<String> roles = new HashSet<>();
        if (realmAccess instanceof java.util.Map<?, ?> map) {
            Object r = map.get("roles");
            if (r instanceof Collection<?> c) {
                for (Object o : c) {
                    roles.add(String.valueOf(o));
                }
            }
        }
        return roles;
    }

    public static Set<String> extrairRoles(Authentication authentication) {
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority a : authentication.getAuthorities()) {
            String auth = a.getAuthority();
            if (auth.startsWith("ROLE_")) {
                roles.add(auth.substring("ROLE_".length()));
            } else {
                roles.add(auth);
            }
        }
        return roles;
    }
}

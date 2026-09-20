package com.credito.relatorio.job;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

public class SolicitacaoRowMapper implements RowMapper<SolicitacaoDecididaRow> {

    @Override
    public SolicitacaoDecididaRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SolicitacaoDecididaRow(
                UUID.fromString(rs.getString("id")),
                rs.getString("status"),
                rs.getBigDecimal("valor"),
                rs.getTimestamp("data_decisao").toInstant());
    }
}

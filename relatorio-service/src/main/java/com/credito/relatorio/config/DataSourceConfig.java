package com.credito.relatorio.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties batchDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    DataSource batchDataSource(DataSourceProperties batchDataSourceProperties) {
        return batchDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConfigurationProperties("app.datasource.solicitacao")
    DataSourceProperties solicitacaoDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    DataSource solicitacaoDataSource(
            @Qualifier("solicitacaoDataSourceProperties") DataSourceProperties solicitacaoDataSourceProperties) {
        return solicitacaoDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    JdbcTemplate solicitacaoJdbcTemplate(@Qualifier("solicitacaoDataSource") DataSource solicitacaoDataSource) {
        return new JdbcTemplate(solicitacaoDataSource);
    }
}

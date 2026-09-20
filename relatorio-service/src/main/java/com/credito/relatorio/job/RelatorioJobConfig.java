package com.credito.relatorio.job;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class RelatorioJobConfig {

    @Bean
    @StepScope
    org.springframework.batch.item.database.JdbcPagingItemReader<SolicitacaoDecididaRow> solicitacaoReader(
            @Qualifier("solicitacaoDataSource") DataSource solicitacaoDataSource,
            @Value("#{jobParameters['data']}") String data)
            throws Exception {
        return new JdbcPagingItemReaderBuilder<SolicitacaoDecididaRow>()
                .name("solicitacaoReader")
                .dataSource(solicitacaoDataSource)
                .queryProvider(queryProvider())
                .parameterValues(Map.of("data", data))
                .pageSize(100)
                .rowMapper(new SolicitacaoRowMapper())
                .build();
    }

    private PagingQueryProvider queryProvider() {
        PostgresPagingQueryProvider provider = new PostgresPagingQueryProvider();
        provider.setSelectClause("id, status, valor, data_decisao");
        provider.setFromClause("solicitacao");
        provider.setWhereClause(
                "status IN ('APROVADA', 'REJEITADA') AND CAST(data_decisao AS DATE) = CAST(:data AS DATE)");
        provider.setSortKeys(Map.of("id", Order.ASCENDING));
        return provider;
    }

    @Bean
    @StepScope
    org.springframework.batch.item.file.FlatFileItemWriter<RelatorioLinha> relatorioWriter(
            @Value("#{jobParameters['data']}") String data, @Value("${app.relatorio.diretorio}") String diretorio)
            throws Exception {
        BeanWrapperFieldExtractor<RelatorioLinha> extractor = new BeanWrapperFieldExtractor<>();
        extractor.setNames(new String[] {"id", "status", "valor", "dataDecisao", "alcada"});
        DelimitedLineAggregator<RelatorioLinha> aggregator = new DelimitedLineAggregator<>();
        aggregator.setDelimiter(",");
        aggregator.setFieldExtractor(extractor);
        return new FlatFileItemWriterBuilder<RelatorioLinha>()
                .name("relatorioWriter")
                .resource(new FileSystemResource(diretorio + "/relatorio-" + data + ".csv"))
                .shouldDeleteIfExists(true)
                .headerCallback(writer -> writer.write("id,status,valor,data_decisao,alcada"))
                .lineAggregator(aggregator)
                .build();
    }

    @Bean
    Job relatorioDiarioJob(JobRepository jobRepository, Step limpezaStep, Step detalheStep, Step resumoStep) {
        return new JobBuilder("relatorioDiarioJob", jobRepository)
                .start(limpezaStep)
                .next(detalheStep)
                .next(resumoStep)
                .build();
    }

    @Bean
    Step limpezaStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager, LimpezaTasklet limpezaTasklet) {
        return new StepBuilder("limpezaStep", jobRepository)
                .tasklet(limpezaTasklet, transactionManager)
                .build();
    }

    @Bean
    Step detalheStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            org.springframework.batch.item.database.JdbcPagingItemReader<SolicitacaoDecididaRow> solicitacaoReader,
            RelatorioProcessor processor,
            org.springframework.batch.item.file.FlatFileItemWriter<RelatorioLinha> relatorioWriter) {
        return new StepBuilder("detalheStep", jobRepository)
                .<SolicitacaoDecididaRow, RelatorioLinha>chunk(100, transactionManager)
                .reader(solicitacaoReader)
                .processor(processor)
                .writer(relatorioWriter)
                .build();
    }

    @Bean
    Step resumoStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager, ResumoTasklet resumoTasklet) {
        return new StepBuilder("resumoStep", jobRepository)
                .tasklet(resumoTasklet, transactionManager)
                .build();
    }
}

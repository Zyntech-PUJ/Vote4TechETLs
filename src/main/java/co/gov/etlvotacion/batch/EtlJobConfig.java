package co.gov.etlvotacion.batch;

import com.fasterxml.jackson.databind.JsonNode;
import co.gov.etlvotacion.entity.VotoSql;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuración del Job Spring Batch para el proceso ETL.
 *
 * Flujo:
 *   VotoItemReader (CouchDB) → VotoItemProcessor (transforma + chequeo idempotencia)
 *   → VotoItemWriter (PostgreSQL)
 *
 * Chunk size de 50: cada 50 votos se abre una transacción y se hace un INSERT en lote.
 */
@Configuration
public class EtlJobConfig {

    @Autowired
    private VotoItemReader votoItemReader;

    @Autowired
    private VotoItemProcessor votoItemProcessor;

    @Autowired
    private VotoItemWriter votoItemWriter;

    @Bean
    public Step etlStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("etlStep", jobRepository)
                .<JsonNode, VotoSql>chunk(50, transactionManager)
                .reader(votoItemReader)
                .processor(votoItemProcessor)
                .writer(votoItemWriter)
                .build();
    }

    @Bean
    public Job etlJob(JobRepository jobRepository, Step etlStep) {
        return new JobBuilder("etlVotacionJob", jobRepository)
                .start(etlStep)
                .build();
    }
}

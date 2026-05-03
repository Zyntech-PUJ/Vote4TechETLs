package co.gov.etlvotacion.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configura tres DataSources:
 *
 *  1. primaryDataSource  — BD principal del ETL (metadatos Spring Batch + tabla VOTO_SQL).
 *                          Se construye con spring.datasource.* y se marca @Primary para que
 *                          JPA y Spring Batch lo elijan como candidato único.
 *
 *  2. nacionalDataSource — BD origen de la sincronización (bd_nacional_vote4tech).
 *
 *  3. publicaDataSource  — BD destino de la sincronización (bd_publica).
 *
 * Al definir los tres beans manualmente, se desactiva la auto-configuración de DataSource
 * de Spring Boot (DataSourceAutoConfiguration) y se evitan ambigüedades.
 */
@Configuration
public class DataSourceConfig {

    // ── 1. Datasource primario (Batch metadata + JPA VOTO_SQL) ──────────────────

    @Primary
    @Bean("dataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean("transactionManager")
    public PlatformTransactionManager primaryTransactionManager() {
        return new DataSourceTransactionManager(primaryDataSource());
    }

    // ── 2. BD Nacional (origen del sync) ───────────────────────────────────────

    @Bean("nacionalDataSource")
    @ConfigurationProperties(prefix = "sync.datasource.nacional")
    public DataSource nacionalDataSource() {
        return DataSourceBuilder.create().build();
    }

    // ── 3. BD Pública (destino del sync) ──────────────────────────────────────

    @Bean("publicaDataSource")
    @ConfigurationProperties(prefix = "sync.datasource.publica")
    public DataSource publicaDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("publicaTransactionManager")
    public PlatformTransactionManager publicaTransactionManager() {
        return new DataSourceTransactionManager(publicaDataSource());
    }
}

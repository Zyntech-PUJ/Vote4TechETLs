package co.gov.etlvotacion.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configura tres DataSources.
 *
 * NOTA: Se usa @Value explícito en lugar de @ConfigurationProperties porque
 * HikariCP expone setJdbcUrl() (no setUrl()), por lo que el binding automático
 * de "spring.datasource.url" no funciona directamente sobre HikariDataSource.
 *
 *  1. dataSource         — BD principal del ETL (metadatos Spring Batch + tabla VOTO_SQL).
 *                          Marcado @Primary para que JPA y Spring Batch lo usen.
 *
 *  2. nacionalDataSource — BD origen de la sincronización (bd_nacional_vote4tech).
 *
 *  3. publicaDataSource  — BD destino de la sincronización (bd_publica).
 */
@Configuration
public class DataSourceConfig {

    // ── 1. Datasource primario (Batch metadata + JPA VOTO_SQL) ──────────────────

    @Primary
    @Bean("dataSource")
    public DataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Primary
    @Bean("transactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    // ── 2. BD Nacional (origen del sync) ───────────────────────────────────────

    @Bean("nacionalDataSource")
    public DataSource nacionalDataSource(
            @Value("${sync.datasource.nacional.url}") String url,
            @Value("${sync.datasource.nacional.username}") String username,
            @Value("${sync.datasource.nacional.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    // ── 3. BD Pública (destino del sync) ──────────────────────────────────────

    @Bean("publicaDataSource")
    public DataSource publicaDataSource(
            @Value("${sync.datasource.publica.url}") String url,
            @Value("${sync.datasource.publica.username}") String username,
            @Value("${sync.datasource.publica.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean("publicaTransactionManager")
    public PlatformTransactionManager publicaTransactionManager(
            @Qualifier("publicaDataSource") DataSource publicaDataSource) {
        return new DataSourceTransactionManager(publicaDataSource);
    }
}

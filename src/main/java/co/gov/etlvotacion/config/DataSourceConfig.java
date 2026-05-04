package co.gov.etlvotacion.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configura el DataSource principal del ETL.
 *
 * NOTA: Se usa @Value explícito en lugar de @ConfigurationProperties porque
 * HikariCP expone setJdbcUrl() (no setUrl()), por lo que el binding automático
 * de "spring.datasource.url" no funciona directamente sobre HikariDataSource.
 */
@Configuration
public class DataSourceConfig {

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
    public PlatformTransactionManager primaryTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}

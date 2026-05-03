package co.gov.etlvotacion.batch;

import co.gov.etlvotacion.entity.VotoSql;
import co.gov.etlvotacion.repository.RepositoryVotoSql;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Persiste los VotoSql procesados en PostgreSQL.
 * Spring Batch garantiza que los chunks se escriben dentro de una transacción.
 */
@Component
public class VotoItemWriter implements ItemWriter<VotoSql> {

    @Autowired
    private RepositoryVotoSql repositoryVotoSql;

    @Override
    public void write(Chunk<? extends VotoSql> chunk) {
        repositoryVotoSql.saveAll(chunk.getItems());
    }
}

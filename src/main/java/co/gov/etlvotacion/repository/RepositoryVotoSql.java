package co.gov.etlvotacion.repository;

import co.gov.etlvotacion.entity.VotoSql;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositoryVotoSql extends JpaRepository<VotoSql, String> {
}

package co.gov.etlvotacion.sync;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job Spring Batch que sincroniza desde bd_nacional_vote4tech hacia bd_publica
 * las cinco tablas que consume el PortalCiudadaniaBack.
 *
 * Orden de los steps (respeta dependencias FK):
 *   1. syncRegistrador    — sin contraseña (campo no necesario en portal público)
 *   2. syncCiudadano      — datos personales para consulta de jurado/sanción
 *   3. syncEleccion       — elecciones activas (FK → REGISTRADOR)
 *   4. syncCandidato      — candidatos (FK → REGISTRADOR)
 *   5. syncEleccionJurado — designaciones de jurado (FK → CIUDADANO + ELECCION)
 *
 * Cada step usa UPSERT (INSERT … ON CONFLICT DO UPDATE) para garantizar
 * idempotencia: se puede re-ejecutar sin duplicados.
 *
 * Chunk size 200: balance entre memoria y número de round-trips a bd_publica.
 */
@Configuration
public class SincronizacionJobConfig {

    private static final int CHUNK_SIZE = 200;

    @Autowired
    @Qualifier("nacionalDataSource")
    private DataSource nacional;

    @Autowired
    @Qualifier("publicaDataSource")
    private DataSource publica;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Crea un lector JDBC que recorre la query dada sobre bd_nacional.
     * ColumnMapRowMapper retorna cada fila como Map<String, Object>,
     * con las claves en minúsculas (nombre de columna PostgreSQL).
     */
    private JdbcCursorItemReader<Map<String, Object>> reader(String stepName, String sql) {
        JdbcCursorItemReader<Map<String, Object>> reader = new JdbcCursorItemReader<>();
        reader.setName(stepName + "Reader");
        reader.setDataSource(nacional);
        reader.setSql(sql);
        reader.setRowMapper(new ColumnMapRowMapper());
        return reader;
    }

    /**
     * Crea un escritor JDBC que ejecuta el upsertSql sobre bd_publica.
     * Usa NamedParameterJdbcTemplate para mapear el Map<String,Object>
     * directamente con los parámetros nombrados (:col_name) del SQL.
     */
    private ItemWriter<Map<String, Object>> writer(String upsertSql) {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(publica);
        return chunk -> chunk.getItems()
                .forEach(row -> jdbc.update(upsertSql, new MapSqlParameterSource(row)));
    }

    /**
     * Ensambla un Step chunk-oriented con el lector/escritor correspondientes.
     * Usa publicaTransactionManager para que el chunk sea transaccional
     * contra bd_publica.
     */
    private Step buildStep(String name,
                           String selectSql,
                           String upsertSql,
                           JobRepository jobRepository,
                           PlatformTransactionManager publicaTm) {
        return new StepBuilder(name, jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(CHUNK_SIZE, publicaTm)
                .reader(reader(name, selectSql))
                .writer(writer(upsertSql))
                .build();
    }

    // ── Job ───────────────────────────────────────────────────────────────────

    @Bean("sincronizacionJob")
    public Job sincronizacionJob(
            JobRepository jobRepository,
            @Qualifier("publicaTransactionManager") PlatformTransactionManager publicaTm) {

        return new JobBuilder("sincronizacionNacionalPublicaJob", jobRepository)

                // 1. REGISTRADOR — solo se sincroniza el id_registrador para satisfacer
                //    la restricción FK de las tablas ELECCION y CANDIDATO en bd_publica.
                //    El registrador no se expone ni se consulta en el portal ciudadano;
                //    nombre, usuario y password se rellenan con placeholders vacíos.
                .start(buildStep("syncRegistrador",
                        "SELECT id_registrador FROM registrador",
                        """
                        INSERT INTO registrador (id_registrador, nombre, usuario, password)
                        VALUES (:id_registrador, '', '', '')
                        ON CONFLICT (id_registrador) DO NOTHING
                        """,
                        jobRepository, publicaTm))

                // 2. CIUDADANO — consultado por cédula (jurado / sanción).
                .next(buildStep("syncCiudadano",
                        "SELECT id_ciudadano, nombre, cedula, genero, voto_obligatorio FROM ciudadano",
                        """
                        INSERT INTO ciudadano (id_ciudadano, nombre, cedula, genero, voto_obligatorio)
                        VALUES (:id_ciudadano, :nombre, :cedula, :genero, :voto_obligatorio)
                        ON CONFLICT (id_ciudadano) DO UPDATE SET
                          nombre          = EXCLUDED.nombre,
                          cedula          = EXCLUDED.cedula,
                          genero          = EXCLUDED.genero,
                          voto_obligatorio = EXCLUDED.voto_obligatorio
                        """,
                        jobRepository, publicaTm))

                // 3. ELECCION — listada en el portal.
                //    La tabla ELECCION en bd_nacional usa id_administrador_electoral (no id_registrador),
                //    por lo que sincronizamos solo los metadatos de la elección sin FK de administrador.
                .next(buildStep("syncEleccion",
                        """
                        SELECT id_eleccion, nombre, fecha_inicio, fecha_finalizacion,
                               fecha_creacion, tipo, lista_abierta, estado
                        FROM eleccion
                        """,
                        """
                        INSERT INTO eleccion (id_eleccion, nombre, fecha_inicio, fecha_finalizacion,
                                             fecha_creacion, tipo, lista_abierta, estado)
                        VALUES (:id_eleccion, :nombre, :fecha_inicio, :fecha_finalizacion,
                                :fecha_creacion, :tipo, :lista_abierta, :estado)
                        ON CONFLICT (id_eleccion) DO UPDATE SET
                          nombre              = EXCLUDED.nombre,
                          fecha_inicio        = EXCLUDED.fecha_inicio,
                          fecha_finalizacion  = EXCLUDED.fecha_finalizacion,
                          fecha_creacion      = EXCLUDED.fecha_creacion,
                          tipo                = EXCLUDED.tipo,
                          lista_abierta       = EXCLUDED.lista_abierta,
                          estado              = EXCLUDED.estado
                        """,
                        jobRepository, publicaTm))

                // 4. CANDIDATO — listado en el portal (FK → REGISTRADOR + ELECCION).
                //    JOIN con LISTA para obtener id_eleccion (la entidad bd_nacional no lo tiene directamente).
                //    JOIN con PARTIDO para obtener logo_url como partido_logo_url.
                //    foto_url no existe como columna VARCHAR en bd_nacional (solo hay BLOB 'foto'),
                //    por lo que se sincroniza como NULL.
                .next(buildStep("syncCandidato",
                        """
                        SELECT c.id_candidato, c.nombre, c.numero,
                               NULL::varchar                AS foto_url,
                               p.logo_url                  AS partido_logo_url,
                               c.id_registrador,
                               l.id_eleccion
                        FROM candidato c
                        JOIN lista l    ON c.id_lista    = l.id_lista
                        LEFT JOIN partido p ON c.id_partido = p.id_partido
                        WHERE c.activo = true
                        """,
                        """
                        INSERT INTO candidato (id_candidato, nombre, numero, foto_url, partido_logo_url,
                                              id_registrador, id_eleccion)
                        VALUES (:id_candidato, :nombre, :numero, :foto_url, :partido_logo_url,
                                :id_registrador, :id_eleccion)
                        ON CONFLICT (id_candidato) DO UPDATE SET
                          nombre           = EXCLUDED.nombre,
                          numero           = EXCLUDED.numero,
                          foto_url         = EXCLUDED.foto_url,
                          partido_logo_url = EXCLUDED.partido_logo_url,
                          id_registrador   = EXCLUDED.id_registrador,
                          id_eleccion      = EXCLUDED.id_eleccion
                        """,
                        jobRepository, publicaTm))

                // 5. ELECCION_JURADO — asignaciones consultadas por cédula (FK → CIUDADANO + ELECCION).
                .next(buildStep("syncEleccionJurado",
                        """
                        SELECT id_asignacion_jurado, id_ciudadano, id_eleccion,
                               tipo_jurado, numero_mesa, fecha_capacitacion, estado
                        FROM eleccion_jurado
                        """,
                        """
                        INSERT INTO eleccion_jurado (id_asignacion_jurado, id_ciudadano, id_eleccion,
                                                    tipo_jurado, numero_mesa, fecha_capacitacion, estado)
                        VALUES (:id_asignacion_jurado, :id_ciudadano, :id_eleccion,
                                :tipo_jurado, :numero_mesa, :fecha_capacitacion, :estado)
                        ON CONFLICT (id_asignacion_jurado) DO UPDATE SET
                          id_ciudadano       = EXCLUDED.id_ciudadano,
                          id_eleccion        = EXCLUDED.id_eleccion,
                          tipo_jurado        = EXCLUDED.tipo_jurado,
                          numero_mesa        = EXCLUDED.numero_mesa,
                          fecha_capacitacion = EXCLUDED.fecha_capacitacion,
                          estado             = EXCLUDED.estado
                        """,
                        jobRepository, publicaTm))

                .build();
    }
}

package co.gov.etlvotacion.controller;

import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST para disparar la sincronización bd_nacional_vote4tech → bd_publica.
 *
 * Tablas sincronizadas (en orden de dependencia FK):
 *   REGISTRADOR → CIUDADANO → ELECCION → CANDIDATO → ELECCION_JURADO
 *
 * El parámetro "run.id" basado en timestamp hace que Spring Batch
 * trate cada llamada como un job distinto, permitiendo re-ejecuciones.
 *
 * La operación es idempotente: usa UPSERT, por lo que ejecutarla
 * varias veces no genera duplicados.
 */
@RestController
@RequestMapping("/etl")
public class SincronizacionController {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("sincronizacionJob")
    private Job sincronizacionJob;

    @PostMapping("/sincronizar")
    public ResponseEntity<Map<String, Object>> sincronizar() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            var execution = jobLauncher.run(sincronizacionJob, params);

            var startTime = execution.getStartTime();
            var endTime   = execution.getEndTime();

            return ResponseEntity.ok(Map.of(
                    "jobId",  execution.getJobId(),
                    "status", execution.getStatus().name(),
                    "inicio", startTime != null ? startTime.toString() : "",
                    "fin",    endTime   != null ? endTime.toString()   : "en proceso"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}

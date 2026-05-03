package co.gov.etlvotacion.controller;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint REST para disparar el proceso ETL manualmente.
 * El jobParameter "run.id" basado en timestamp garantiza que Spring Batch
 * trate cada ejecución como un job distinto (no rechaza por "ya ejecutado").
 */
@RestController
@RequestMapping("/etl")
public class EtlController {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job etlJob;

    @PostMapping("/ejecutar")
    public ResponseEntity<Map<String, Object>> ejecutar() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            var execution = jobLauncher.run(etlJob, params);

            return ResponseEntity.ok(Map.of(
                    "jobId", execution.getJobId(),
                    "status", execution.getStatus().name(),
                    "inicio", execution.getStartTime() != null ? execution.getStartTime().toString() : "",
                    "fin", execution.getEndTime() != null ? execution.getEndTime().toString() : "en proceso"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}

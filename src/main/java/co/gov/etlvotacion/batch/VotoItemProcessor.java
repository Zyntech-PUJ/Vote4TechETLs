package co.gov.etlvotacion.batch;

import co.gov.etlvotacion.entity.VotoSql;
import co.gov.etlvotacion.repository.RepositoryVotoSql;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Transforma un documento JSON de CouchDB en una entidad VotoSql.
 * Devuelve null si el voto ya existe en PostgreSQL (Spring Batch omite nulos,
 * garantizando idempotencia sin lanzar errores).
 */
@Component
public class VotoItemProcessor implements ItemProcessor<JsonNode, VotoSql> {

    @Autowired
    private RepositoryVotoSql repositoryVotoSql;

    @Override
    public VotoSql process(JsonNode doc) {
        String id = doc.path("_id").asText();
        if (repositoryVotoSql.existsById(id)) {
            return null; // Spring Batch omite el item — idempotente
        }
        return VotoSql.builder()
                .idVoto(id)
                .idEleccion(nullableLong(doc, "idEleccion"))
                .idMesa(nullableLong(doc, "idMesa"))
                .tipoMesa(doc.path("tipoMesa").asText(null))
                .idCentroVotacion(nullableLong(doc, "idCentroVotacion"))
                .tipoSeleccion(doc.path("tipoSeleccion").asText(null))
                .idSeleccion(nullableLong(doc, "idSeleccion"))
                .timestamp(doc.path("timestamp").asText(null))
                .build();
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isNull() || n.isMissingNode() || n.asText().isBlank()) ? null : n.asLong();
    }
}

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
        Long idSeleccion = nullableLong(doc, "idSeleccion");
        String tipoSel = doc.path("tipoSeleccion").asText(null);
        // idSeleccion es el candidato cuando tipoSeleccion=CANDIDATO o cuando no hay tipoSeleccion (domicilio)
        Long idCandidato = (tipoSel == null || "CANDIDATO".equals(tipoSel)) ? idSeleccion : null;

        return VotoSql.builder()
                .idVoto(id)
                .idEleccion(nullableLong(doc, "idEleccion"))
                .idMesa(nullableLong(doc, "idMesa"))
                .tipoMesa(doc.path("tipoMesa").asText(null))
                .idCentroVotacion(nullableLong(doc, "idCentroVotacion"))
                .tipoSeleccion(tipoSel)
                .idSeleccion(idSeleccion)
                .idCandidato(idCandidato)
                .timestamp(doc.path("timestamp").asText(null))
                .fuente(doc.path("_fuente").asText(null))
                .build();
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isNull() || n.isMissingNode() || n.asText().isBlank()) ? null : n.asLong();
    }
}

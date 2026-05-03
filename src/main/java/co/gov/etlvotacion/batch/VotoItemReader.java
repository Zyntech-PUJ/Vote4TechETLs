package co.gov.etlvotacion.batch;

import co.gov.etlvotacion.couchdb.CouchDbClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

/**
 * Lee todos los documentos de voto de CouchDB (votos_urna + votos_domicilio).
 * La carga se realiza en el primer read() y luego se itera sobre los documentos
 * en memoria, devolviendo null al finalizar (señal de fin de lectura para Spring Batch).
 */
@Component
public class VotoItemReader implements ItemReader<JsonNode> {

    @Autowired
    private CouchDbClient couchDbClient;

    private Iterator<JsonNode> iterator;

    @Override
    public JsonNode read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (iterator == null) {
            List<JsonNode> docs = couchDbClient.getAllVotos();
            iterator = docs.iterator();
        }
        if (iterator.hasNext()) {
            return iterator.next();
        }
        // Reiniciar para que el job pueda ser ejecutado de nuevo en la misma instancia
        iterator = null;
        return null;
    }
}

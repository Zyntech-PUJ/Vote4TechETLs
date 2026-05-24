package co.gov.etlvotacion.couchdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Cliente HTTP para CouchDB.
 * Recupera todos los documentos de las bases votos_urna y votos_domicilio.
 */
@Service
public class CouchDbClient {

    @Value("${couchdb.url}")
    private String couchDbUrl;

    @Value("${couchdb.username}")
    private String couchDbUsername;

    @Value("${couchdb.password}")
    private String couchDbPassword;

    @Value("${couchdb.database.urna:votos_urna}")
    private String dbUrna;

    @Value("${couchdb.database.domicilio:votos_domicilio}")
    private String dbDomicilio;

    private RestClient restClient;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        String credentials = couchDbUsername + ":" + couchDbPassword;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl(couchDbUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.objectMapper = new ObjectMapper();
    }

    /**
     * Recupera todos los documentos de voto de ambas bases CouchDB.
     * Excluye los documentos de diseño (_id que empiece por _design/).
     */
    public List<JsonNode> getAllVotos() {
        List<JsonNode> todos = new ArrayList<>();
        todos.addAll(fetchFromDb(dbUrna, "URNA"));
        todos.addAll(fetchFromDb(dbDomicilio, "DOMICILIO"));
        return todos;
    }

    private List<JsonNode> fetchFromDb(String db, String fuente) {
        try {
            String response = restClient.get()
                    .uri("/" + db + "/_all_docs?include_docs=true")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            List<JsonNode> docs = new ArrayList<>();
            for (JsonNode row : root.path("rows")) {
                JsonNode doc = row.path("doc");
                if (!doc.path("_id").asText().startsWith("_design/")) {
                    ((ObjectNode) doc).put("_fuente", fuente);
                    docs.add(doc);
                }
            }
            return docs;
        } catch (Exception e) {
            throw new RuntimeException("Error al leer CouchDB (" + db + "): " + e.getMessage(), e);
        }
    }

    public String getDbUrna() {
        return dbUrna;
    }

    public String getDbDomicilio() {
        return dbDomicilio;
    }
}

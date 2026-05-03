package co.gov.etlvotacion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabla destino del proceso ETL.
 * Almacena votos normalizados (CouchDB → SQL) para consulta relacional.
 * El id_voto corresponde al _id del documento en CouchDB, garantizando idempotencia.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "VOTO_SQL")
public class VotoSql {

    /** UUID del voto (mismo _id de CouchDB — garantiza idempotencia) */
    @Id
    @Column(name = "id_voto", length = 64)
    private String idVoto;

    @Column(name = "id_eleccion")
    private Long idEleccion;

    @Column(name = "id_mesa")
    private Long idMesa;

    @Column(name = "tipo_mesa", length = 16)
    private String tipoMesa;

    @Column(name = "id_centro_votacion")
    private Long idCentroVotacion;

    @Column(name = "tipo_seleccion", length = 16)
    private String tipoSeleccion;

    @Column(name = "id_seleccion")
    private Long idSeleccion;

    @Column(name = "timestamp_voto", length = 32)
    private String timestamp;
}

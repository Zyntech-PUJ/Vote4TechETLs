# Guía de Despliegue — EtlVotacion

ETL independiente basado en **Spring Batch** que extrae votos de CouchDB (`votos_urna` y `votos_domicilio`) y los ingesta en la tabla `VOTO_SQL` de PostgreSQL. Se despliega en la misma VM que las bases de datos (`10.43.101.13`) para minimizar latencia en las operaciones de lectura/escritura masiva.

> El ETL corre en el **puerto 8083** y no expone ninguna UI — solo un endpoint REST para disparar el proceso.

---

## Infraestructura

| Servicio            | VM Producción   | Puerto |
|---------------------|-----------------|--------|
| **EtlVotacion**     | `10.43.101.13`  | `8083` |
| PostgreSQL          | `10.43.101.13`  | `5432` |
| CouchDB             | `10.43.101.13`  | `5984` |

Al estar en la misma VM que las BDs, se usa `network_mode: host` — el ETL accede a PostgreSQL y CouchDB via `localhost`.

---

## Disparar el ETL (uso normal)

```bash
curl -X POST http://10.43.101.13:8083/etl/ejecutar
```

Respuesta esperada:

```json
{
  "jobId": 1,
  "status": "COMPLETED",
  "inicio": "2026-05-03T10:00:00",
  "fin": "2026-05-03T10:00:05"
}
```

El proceso es **idempotente**: votos ya existentes en `VOTO_SQL` se omiten automáticamente.

---

## Ejecución Local

Para probar sin acceso a los VMs de producción.

### Paso 1 — Levantar las BDs

Desde la carpeta raíz del workspace:

```bash
docker compose up -d postgres couchdb
```

### Paso 2 — Exportar variables y arrancar

En Linux/Mac:

```bash
cd Vote4TechETLs
export DB_URL="jdbc:postgresql://localhost:5432/vote4tech"
export DB_USER="postgres"
export DB_PASSWORD="postgres123"
export COUCHDB_URL="http://localhost:5984"
export COUCHDB_USER="admin"
export COUCHDB_PASSWORD="admin123"
mvn spring-boot:run
```

En Windows (PowerShell):

```powershell
cd Vote4TechETLs
$env:DB_URL = "jdbc:postgresql://localhost:5432/vote4tech"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "postgres123"
$env:COUCHDB_URL = "http://localhost:5984"
$env:COUCHDB_USER = "admin"
$env:COUCHDB_PASSWORD = "admin123"
mvn spring-boot:run
```

### Paso 3 — Disparar el ETL

```bash
curl -X POST http://localhost:8083/etl/ejecutar
```

---

## Despliegue en Producción (VM `10.43.101.13`)

### Paso 1 — Clonar el repositorio

```bash
cd ~
git clone https://github.com/Zyntech-PUJ/Vote4TechETLs.git
```

Si ya existe:

```bash
cd ~/Vote4TechETLs
git pull
```

### Paso 2 — Construir y levantar

```bash
cd ~/Vote4TechETLs
docker compose -f docker/docker-compose.prod.yml up -d --build
```

> `--build` es obligatorio — el ETL es Java y necesita compilarse con Maven dentro del contenedor.

Ver progreso:

```bash
docker logs -f vote4tech-etl
```

Esperar:
```
Started EtlVotacionApplication in X.XXX seconds
```

Verificar:

```bash
docker ps | grep vote4tech-etl
curl -X POST http://localhost:8083/etl/ejecutar
```

### Paso 3 — Actualizar (sin reconstruir)

Si solo cambian variables de entorno:

```bash
# Editar docker/docker-compose.prod.yml localmente y subir con scp
scp docker/docker-compose.prod.yml estudiante@10.43.101.13:~/Vote4TechETLs/docker/docker-compose.prod.yml

# En el VM (sin --build):
docker compose -f docker/docker-compose.prod.yml up -d
```

---

## Variables de Entorno

| Variable             | Descripción                             | Default         |
|----------------------|-----------------------------------------|-----------------|
| `DB_URL`             | URL JDBC de PostgreSQL                  | *(obligatorio)* |
| `DB_USER`            | Usuario PostgreSQL                      | *(obligatorio)* |
| `DB_PASSWORD`        | Contraseña PostgreSQL                   | *(obligatorio)* |
| `COUCHDB_URL`        | URL base de CouchDB                     | `http://localhost:5984` |
| `COUCHDB_USER`       | Usuario CouchDB                         | `admin`         |
| `COUCHDB_PASSWORD`   | Contraseña CouchDB                      | `admin`         |
| `COUCHDB_DB_URNA`    | Nombre de la base CouchDB de urna       | `votos_urna`    |
| `COUCHDB_DB_DOMICILIO` | Nombre de la base CouchDB de domicilio | `votos_domicilio` |
| `PORT`               | Puerto del servidor                     | `8083`          |

---

## Spring Batch — Metadata

Spring Batch crea automáticamente sus tablas de metadata (`BATCH_JOB_*`, `BATCH_STEP_*`) en el mismo PostgreSQL configurado. Esto permite rastrear el historial de ejecuciones.

Para consultar ejecuciones pasadas:

```sql
SELECT job_instance_id, job_name, create_time, status
FROM BATCH_JOB_EXECUTION
ORDER BY create_time DESC
LIMIT 10;
```

---

## Problemas Conocidos

### Problema: Status `FAILED` en la respuesta

```bash
curl -X POST http://localhost:8083/etl/ejecutar
# {"status": "FAILED", "error": "..."}
```

**Diagnóstico:**

```bash
docker logs vote4tech-etl --tail=50
```

**Causas comunes:**
- No puede conectar a CouchDB — verificar `COUCHDB_URL` y que el servicio esté corriendo
- No puede conectar a PostgreSQL — verificar `DB_URL`
- La tabla `VOTO_SQL` no existe — asegurarse de que VotacionBack se ejecutó al menos una vez (crea la tabla via `ddl-auto=update`), o crearla manualmente

### Problema: Contenedor reinicia en bucle

```bash
docker logs vote4tech-etl --tail=30
```

**Causa más común:** `DB_URL` mal configurado o PostgreSQL no disponible en el momento del arranque.

### Problema: Tablas BATCH_JOB_* no se crean

Verificar que `spring.batch.jdbc.initialize-schema=always` está en `application.properties` y que el usuario de PostgreSQL tiene permisos de `CREATE TABLE`.

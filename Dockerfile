# ============================================================
# ETAPA 1: Build con Maven
# ============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ============================================================
# ETAPA 2: Runtime mínimo con JRE 21
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

COPY --from=builder /app/target/EtlVotacion-0.0.1-SNAPSHOT.jar app.jar

# Variables de entorno requeridas:
# DB_URL, DB_USER, DB_PASSWORD
# COUCHDB_URL, COUCHDB_USER, COUCHDB_PASSWORD, COUCHDB_DB_URNA, COUCHDB_DB_DOMICILIO
ENV PORT=8083

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]

package com.wrda.infrastructure.idempotency.persistence;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Ejecuta db/schema.sql al arrancar (CREATE ... IF NOT EXISTS, es seguro repetirlo). */
@ApplicationScoped
public class SchemaInitializer {

    private static final Logger LOG = Logger.getLogger(SchemaInitializer.class);

    @ConfigProperty(name = "wrda.idempotency.schema-resource", defaultValue = "/db/schema.sql")
    String schemaResource;

    private final AgroalDataSource dataSource;

    @Inject
    public SchemaInitializer(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    void onStart(@Observes StartupEvent event) throws IOException, SQLException {
        String ddl;
        try (InputStream in = getClass().getResourceAsStream(schemaResource)) {
            if (in == null) {
                throw new IllegalStateException("No se encontro " + schemaResource + " en el classpath");
            }
            ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : ddl.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
        LOG.infof("Esquema de base de datos listo desde %s", schemaResource);
    }
}

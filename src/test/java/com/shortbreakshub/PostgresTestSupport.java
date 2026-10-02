package com.shortbreakshub;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** All Spring persistence tests must override the application's external datasource. */
@ActiveProfiles("prod")
public abstract class PostgresTestSupport {
    protected static final EmbeddedPostgres POSTGRES = startPostgres();

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().setPort(0).start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { postgres.close(); } catch (Exception ignored) { }
            }));
            return postgres;
        } catch (Exception failure) {
            throw new IllegalStateException("Isolated PostgreSQL could not start; refusing external DB fallback", failure);
        }
    }

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> false);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
        registry.add("security.jwt.secret", () -> "destination-test-secret-at-least-thirty-two-characters");
    }
}

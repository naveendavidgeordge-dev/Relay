package com.relay.testing;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres 16 container shared by every integration test in the JVM (started once, reaped by
 * Testcontainers' Ryuk on exit). Tagged {@code integration}; run unit tests alone with {@code
 * ./gradlew test -PskipIntegration}.
 */
@Tag("integration")
public abstract class PostgresContainerSupport {

  @SuppressWarnings("resource")
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("relay")
          .withUsername("relay")
          .withPassword("relay");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}

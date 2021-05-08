package org.iinegve.postgres;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import lombok.SneakyThrows;

import java.util.Map;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

  private EmbeddedPostgres postgres;

  @SneakyThrows
  @Override
  public Map<String, String> start() {
    postgres = EmbeddedPostgres.builder().start();

    return Map.of(
      "quarkus.datasource.jdbc.url", postgres.getJdbcUrl("postgres", "postgres")
    );
  }

  @SneakyThrows
  @Override
  public void stop() {
    postgres.close();
  }
}

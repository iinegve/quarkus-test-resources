package org.iinegve.kafka;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

  private final KafkaContainer kafka = new KafkaContainer(
    DockerImageName.parse("confluentinc/cp-kafka:6.1.0")
  );

  @Override
  public Map<String, String> start() {
    kafka.start();

    return Map.of(
      "kafka.bootstrap.servers", kafka.getBootstrapServers()
    );
  }

  @Override
  public void stop() {
    kafka.close();
  }
}

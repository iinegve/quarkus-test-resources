package org.iinegve.kafka;

import com.google.common.collect.Iterables;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.UUIDDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
class KafkaRepositoryIT {

  @Inject
  KafkaRepository kafka;

  @ConfigProperty(name = "kafka.bootstrap.servers")
  String bootstrapServers;

  @ConfigProperty(name = "mp.messaging.outgoing.simple-message.topic")
  String topic;


  @Test
  void simpleMessage__should_be_received_by_consumer__when_sent_out() {
    KafkaConsumer<UUID, SimpleMessage> consumer = initializeKafkaConsumer(topic);

    UUID id = randomUUID();
    // At the moment, when first message is sent, KafkaConsumer cannot join the group.
    // When it finally joins, it reset offset to 1 and very first message is lost.
    // For loop kinda solves this issue.
    for (int i = 0; i < 20; i++) {
      kafka.send(SimpleMessage.builder()
        .id(id)
        .message("Short and simple")
        .build());

      ConsumerRecords<?, SimpleMessage> records = consumer.poll(Duration.of(1000, MILLIS));
      if (records.count() > 0) {
        ConsumerRecord<?, SimpleMessage> record = Iterables.get(records, 0);
        log.info("Received record from kafka, [{}]", record.value());
        assertThat(record.value().getId()).isEqualTo(id);
        assertThat(record.value().getMessage()).isEqualTo("Short and simple");
        return;
      }
    }
    throw new IllegalStateException("No messages have been received");
  }

  @SneakyThrows
  private <T> KafkaConsumer<UUID, T> initializeKafkaConsumer(String topic) {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ConsumerConfig.GROUP_ID_CONFIG, randomAlphanumeric(5));
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, UUIDDeserializer.class.getName());
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SimpleMessageDeserializer.class);

    KafkaConsumer<UUID, T> consumer = new KafkaConsumer<>(config);
    consumer.subscribe(List.of(topic));

    return consumer;
  }
}
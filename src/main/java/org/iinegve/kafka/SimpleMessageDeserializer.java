package org.iinegve.kafka;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class SimpleMessageDeserializer extends ObjectMapperDeserializer<SimpleMessage> {

  public SimpleMessageDeserializer() {
    super(SimpleMessage.class);
  }
}

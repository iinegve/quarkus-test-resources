package org.iinegve.kafka;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import javax.enterprise.context.ApplicationScoped;

@Slf4j
@ApplicationScoped
public class KafkaRepository {

  @Channel("simple-message")
  Emitter<SimpleMessage> emitter;

  public void send(SimpleMessage msg) {
    emitter.send(msg);
    log.debug("Message sent");
  }
}

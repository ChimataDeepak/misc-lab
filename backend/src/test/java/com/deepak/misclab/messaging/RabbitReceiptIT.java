package com.deepak.misclab.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.deepak.misclab.Receipt;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real RabbitMQ 3.13 broker: topic exchange -> consumer, and poison messages -> dead-letter queue. */
@SpringBootTest(properties = {"misclab.aws.init-resources=false", "misclab.poller.enabled=false", "misclab.rabbit.enabled=true"})
@Testcontainers
class RabbitReceiptIT {
    @Container @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired RabbitReceiptPublisher publisher;
    @Autowired LedgerReceiptConsumer consumer;
    @Autowired RabbitTemplate rabbit;

    static Receipt receipt(String currency, String usd) {
        return new Receipt(UUID.randomUUID().toString(), "deepak", new BigDecimal("100"), currency, new BigDecimal(usd), Instant.now());
    }

    /** Tests share the DLQ, so look for this test's own message. */
    Message deadLetterContaining(String marker) {
        return await().atMost(Duration.ofSeconds(20)).until(() -> {
            Message m = rabbit.receive(RabbitReceiptConfig.DLQ);
            return m != null && new String(m.getBody(), StandardCharsets.UTF_8).contains(marker) ? m : null;
        }, m -> m != null);
    }

    @Test void publishedReceiptIsConsumedOnceEvenIfRedelivered() {
        Receipt r = receipt("INR", "1.20");
        publisher.onReceiptCreated(r, "{}");
        publisher.onReceiptCreated(r, "{}");            // duplicate delivery (at-least-once)
        await().atMost(Duration.ofSeconds(20)).until(() -> consumer.hasProcessed(r.receiptId()));
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(consumer.usdTotal("INR")).isEqualByComparingTo("1.20"));
    }

    @Test void malformedJsonGoesToTheDeadLetterQueue() {
        Message poison = MessageBuilder.withBody("{not json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId("poison-json").build();
        rabbit.send(RabbitReceiptConfig.EXCHANGE, "receipt.created.inr", poison);
        Message dead = deadLetterContaining("{not json");
        assertThat(dead.getMessageProperties().getXDeathHeader()).isNotEmpty();   // broker recorded why/where it died
        assertThat(dead.getMessageProperties().getXDeathHeader().get(0).get("reason")).isEqualTo("rejected");
    }

    @Test void contractViolationGoesToTheDeadLetterQueueAndIsNotCounted() {
        Receipt bad = receipt("EUR", "-5.00");           // negative USD amount: parses, but breaks the contract
        publisher.onReceiptCreated(bad, "{}");
        Message dead = deadLetterContaining(bad.receiptId());
        assertThat(dead).isNotNull();
        assertThat(consumer.hasProcessed(bad.receiptId())).isFalse();
        assertThat(consumer.usdTotal("EUR")).isEqualByComparingTo("0");
    }
}

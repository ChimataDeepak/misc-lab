package com.deepak.misclab.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class PaymentConfig {
    @Bean Clock paymentClock() { return Clock.systemUTC(); }

    @Bean IdempotencyStore idempotencyStore(JdbcTemplate jdbc, ObjectMapper json) { return new IdempotencyStore(jdbc, json); }

    @Bean
    WebhookPublisher webhookPublisher(@Value("${misclab.payments.webhook-secret}") String secret,
                                      @Value("${misclab.payments.webhook-url:}") String url,
                                      Clock paymentClock, ObjectMapper json, JdbcTemplate jdbc) {
        return new WebhookPublisher(secret, url, paymentClock, json, jdbc);
    }

    @Bean
    PaymentService paymentService(PaymentRepository repo, IdempotencyStore idem, WebhookPublisher webhooks,
                                  PlatformTransactionManager txManager, Clock paymentClock) {
        return new PaymentService(repo, idem, webhooks, new TransactionTemplate(txManager), paymentClock);
    }

    @Bean
    WebhookVerifier webhookVerifier(@Value("${misclab.payments.webhook-secret}") String secret, Clock paymentClock, JdbcTemplate jdbc) {
        return new WebhookVerifier(secret, Duration.ofMinutes(5), paymentClock, SeenEventStore.jdbc(jdbc));
    }
}

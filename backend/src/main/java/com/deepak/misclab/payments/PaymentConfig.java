package com.deepak.misclab.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {
    @Bean Clock paymentClock() { return Clock.systemUTC(); }

    @Bean IdempotencyStore idempotencyStore() { return new IdempotencyStore(); }

    @Bean
    WebhookPublisher webhookPublisher(@Value("${misclab.payments.webhook-secret}") String secret,
                                      @Value("${misclab.payments.webhook-url:}") String url,
                                      Clock paymentClock, ObjectMapper json) {
        return new WebhookPublisher(secret, url, paymentClock, json);
    }

    @Bean
    PaymentService paymentService(IdempotencyStore idem, WebhookPublisher webhooks, Clock paymentClock) {
        return new PaymentService(idem, webhooks, paymentClock);
    }

    @Bean
    WebhookVerifier webhookVerifier(@Value("${misclab.payments.webhook-secret}") String secret, Clock paymentClock) {
        return new WebhookVerifier(secret, Duration.ofMinutes(5), paymentClock);
    }
}

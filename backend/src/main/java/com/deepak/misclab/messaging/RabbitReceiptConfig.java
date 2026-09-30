package com.deepak.misclab.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology (declared by RabbitAdmin on first connection):
 * <pre>
 *  receipts.events (topic) --receipt.created.*--> receipts.created.ledger
 *                                                     | reject (no requeue)
 *                                                     v
 *  receipts.dlx (direct) --receipts.created.ledger.dlq--> receipts.created.ledger.dlq
 * </pre>
 * Routing key is {@code receipt.created.<currency>}, so e.g. an INR-only consumer could bind {@code receipt.created.inr}.
 */
@Configuration
@ConditionalOnProperty(name = "misclab.rabbit.enabled", havingValue = "true")
public class RabbitReceiptConfig {
    public static final String EXCHANGE = "receipts.events";
    public static final String QUEUE = "receipts.created.ledger";
    public static final String DLX = "receipts.dlx";
    public static final String DLQ = QUEUE + ".dlq";

    @Bean TopicExchange receiptsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean DirectExchange receiptsDeadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean Queue receiptsCreatedQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();
    }
    @Bean Queue receiptsCreatedDlq() { return QueueBuilder.durable(DLQ).build(); }

    @Bean Binding receiptsCreatedBinding() {
        return BindingBuilder.bind(receiptsCreatedQueue()).to(receiptsExchange()).with("receipt.created.*");
    }
    @Bean Binding receiptsDlqBinding() {
        return BindingBuilder.bind(receiptsCreatedDlq()).to(receiptsDeadLetterExchange()).with(DLQ);
    }

    /** JSON on the wire; RabbitTemplate and the listener container both pick this bean up. */
    @Bean MessageConverter rabbitJson(com.fasterxml.jackson.databind.ObjectMapper json) { return new Jackson2JsonMessageConverter(json); }
}

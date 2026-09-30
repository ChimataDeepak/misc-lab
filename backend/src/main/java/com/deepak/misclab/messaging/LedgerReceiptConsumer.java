package com.deepak.misclab.messaging;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes ReceiptCreated and keeps a per-currency USD total (stand-in for a ledger/reporting projection).
 * <ul>
 *   <li><b>Idempotent</b>: RabbitMQ is at-least-once, so a redelivered receiptId is ignored.</li>
 *   <li><b>Poison messages</b>: bad JSON fails conversion (fatal, rejected without requeue); a message that parses
 *       but breaks the contract is rejected with {@link AmqpRejectAndDontRequeueException}. Both land on the DLQ
 *       via the queue's dead-letter exchange instead of blocking the queue in a redelivery loop.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "misclab.rabbit.enabled", havingValue = "true")
public class LedgerReceiptConsumer {
    private static final Logger log = LoggerFactory.getLogger(LedgerReceiptConsumer.class);

    private final Map<String, BigDecimal> processed = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> usdByCurrency = new ConcurrentHashMap<>();

    @RabbitListener(queues = RabbitReceiptConfig.QUEUE)
    public void onReceiptCreated(ReceiptCreatedMessage m) {
        if (m.receiptId() == null || m.usdAmount() == null || m.usdAmount().signum() <= 0) {
            throw new AmqpRejectAndDontRequeueException("invalid ReceiptCreated: " + m);
        }
        if (processed.putIfAbsent(m.receiptId(), m.usdAmount()) != null) {
            log.info("duplicate ReceiptCreated {} ignored", m.receiptId());
            return;
        }
        usdByCurrency.merge(m.currency(), m.usdAmount(), BigDecimal::add);
    }

    public boolean hasProcessed(String receiptId) { return processed.containsKey(receiptId); }
    public BigDecimal usdTotal(String currency) { return usdByCurrency.getOrDefault(currency, BigDecimal.ZERO); }
}

package com.deepak.misclab.messaging;

import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptEventSink;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Plugs into ReceiptService's extra sinks: called only after all receipt writes succeeded. */
@Component
@ConditionalOnProperty(name = "misclab.rabbit.enabled", havingValue = "true")
public class RabbitReceiptPublisher implements ReceiptEventSink {
    private final RabbitTemplate rabbit;

    public RabbitReceiptPublisher(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    @Override
    public void onReceiptCreated(Receipt r, String receiptJson) {
        var msg = new ReceiptCreatedMessage(r.receiptId(), r.payer(), r.currency(), r.usdAmount(), r.createdAt());
        rabbit.convertAndSend(RabbitReceiptConfig.EXCHANGE, "receipt.created." + r.currency().toLowerCase(), msg,
                m -> { m.getMessageProperties().setMessageId(r.receiptId()); return m; });
    }
}

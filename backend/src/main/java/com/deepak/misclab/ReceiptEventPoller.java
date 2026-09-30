package com.deepak.misclab;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/** Simple SQS consumer: long-poll, handle, then delete (delete = ack; undeleted messages reappear). */
@Component
@ConditionalOnProperty(name = "misclab.poller.enabled", havingValue = "true")
public class ReceiptEventPoller {
    private static final Logger log = LoggerFactory.getLogger(ReceiptEventPoller.class);
    private final SqsClient sqs; private final ReceiptService service; private final Counter consumed;

    public ReceiptEventPoller(SqsClient sqs, ReceiptService service, MeterRegistry registry) {
        this.sqs = sqs; this.service = service;
        this.consumed = Counter.builder("receipt_events_consumed").register(registry);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void poll() {
        try {
            String url = service.queueUrl();
            for (Message m : sqs.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1)).messages()) {
                log.info("consumed event {}", m.body());
                consumed.increment();
                sqs.deleteMessage(b -> b.queueUrl(url).receiptHandle(m.receiptHandle()));
            }
        } catch (Exception e) {
            log.warn("SQS poll failed: {}", e.getMessage());
        }
    }
}

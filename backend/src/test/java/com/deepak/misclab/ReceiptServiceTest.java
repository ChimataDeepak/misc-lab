package com.deepak.misclab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@SuppressWarnings("unchecked")
class ReceiptServiceTest {
    FxClient fx = mock(FxClient.class);
    DynamoDbTable<ReceiptItem> table = mock(DynamoDbTable.class);
    S3Client s3 = mock(S3Client.class);
    SqsClient sqs = mock(SqsClient.class);
    AuditLogRepository audit = mock(AuditLogRepository.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AwsProps props = new AwsProps("http://x", "ap-south-1", "test", "test", "misc-receipts", "receipt-events", "receipts", false);
    ReceiptService service = new ReceiptService(fx, table, s3, sqs, audit, props, new ObjectMapper().findAndRegisterModules(), registry);

    @Test
    void createWritesToAllStoresAndCountsMetric() {
        when(fx.rateToUsd("INR")).thenReturn(new BigDecimal("0.012"));
        when(sqs.getQueueUrl(any(Consumer.class))).thenReturn(GetQueueUrlResponse.builder().queueUrl("q").build());

        Receipt r = service.create(new Receipt.Request("deepak", new BigDecimal("1000"), "INR"));

        assertThat(r.usdAmount()).isEqualByComparingTo("12.00");
        verify(table).putItem(any(ReceiptItem.class));
        verify(s3).putObject(any(Consumer.class), any(RequestBody.class));
        verify(sqs).sendMessage(any(Consumer.class));
        verify(audit).save(any(AuditLog.class));
        assertThat(registry.counter("receipts_issued").count()).isEqualTo(1.0);
    }

    /**
     * Proof of parallelism: every FX call blocks on a latch that only opens once ALL n calls have arrived.
     * Sequential execution would deadlock (first call waits forever -> 2s timeout -> exception).
     */
    @Test
    void batchRunsConversionsInParallel() {
        int n = 20;
        CountDownLatch allArrived = new CountDownLatch(n);
        when(fx.rateToUsd("INR")).thenAnswer(inv -> {
            allArrived.countDown();
            if (!allArrived.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("calls were not concurrent");
            return new BigDecimal("0.012");
        });
        List<Receipt.Request> reqs = IntStream.range(0, n)
                .mapToObj(i -> new Receipt.Request("p" + i, BigDecimal.TEN, "INR")).toList();

        List<Receipt> out = service.convertBatch(reqs);

        assertThat(out).hasSize(n).allSatisfy(r -> assertThat(r.usdAmount()).isEqualByComparingTo("0.12"));
        assertThat(out).extracting(Receipt::payer).containsExactlyElementsOf(reqs.stream().map(Receipt.Request::payer).toList());
    }
}

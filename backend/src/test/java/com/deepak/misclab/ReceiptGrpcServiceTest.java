package com.deepak.misclab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepak.misclab.grpc.GetReceiptRequest;
import com.deepak.misclab.grpc.ReceiptReply;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ReceiptGrpcServiceTest {

    @Test
    void getReceiptReturnsProtoFields() throws Exception {
        ReceiptService svc = mock(ReceiptService.class);
        when(svc.get("r1")).thenReturn(new Receipt(
                "r1", "deepak", new BigDecimal("1000"), "INR",
                new BigDecimal("12.00"), Instant.parse("2026-09-30T00:00:00Z")));

        ReceiptReply reply = call(new ReceiptGrpcService(svc), "r1");
        assertThat(reply.getFound()).isTrue();
        assertThat(reply.getPayer()).isEqualTo("deepak");
        assertThat(reply.getUsdAmount()).isEqualTo("12.00");
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        ReceiptReply reply = call(new ReceiptGrpcService(mock(ReceiptService.class)), "nope");
        assertThat(reply.getFound()).isFalse();
    }

    private static ReceiptReply call(ReceiptGrpcService grpc, String id) throws Exception {
        CollectingObserver rec = new CollectingObserver();
        grpc.getReceipt(GetReceiptRequest.newBuilder().setId(id).build(), rec);
        assertThat(rec.done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.errors).isEmpty();
        return rec.values.get(0);
    }

    private static final class CollectingObserver implements StreamObserver<ReceiptReply> {
        final List<ReceiptReply> values = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);

        @Override public void onNext(ReceiptReply value) { values.add(value); }
        @Override public void onError(Throwable t) { errors.add(t); done.countDown(); }
        @Override public void onCompleted() { done.countDown(); }
    }
}

package com.deepak.misclab;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;

/** Full POST flow against real LocalStack (S3, SQS, DynamoDB) + MongoDB containers; WireMock for FX. */
@Testcontainers
@AutoConfigureObservability // @SpringBootTest disables metrics export by default
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReceiptFlowIT {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(Service.S3, Service.SQS, Service.DYNAMODB);

    @Container @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @RegisterExtension
    static WireMockExtension fxApi = WireMockExtension.newInstance().options(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("misclab.aws.endpoint", () -> localstack.getEndpoint().toString());
        r.add("misclab.aws.region", localstack::getRegion);
        r.add("fx.base-url", fxApi::baseUrl);
    }

    @Autowired TestRestTemplate http;
    @Autowired S3Client s3;
    @Autowired AuditLogRepository audit;
    @Autowired MeterRegistry registry;
    @Autowired ReceiptService service;

    @Test
    void postReceiptPersistsEverywhereAndEmitsEvent() {
        fxApi.stubFor(get(urlPathEqualTo("/rates")).willReturn(okJson("{\"rate\": 0.012}")));

        var res = http.postForEntity("/api/receipts",
                new Receipt.Request("deepak", new BigDecimal("1000"), "INR"), Receipt.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Receipt r = res.getBody();
        assertThat(r.usdAmount()).isEqualByComparingTo("12.00");
        // DynamoDB round-trip via the enhanced client
        assertThat(service.get(r.receiptId()).payer()).isEqualTo("deepak");
        // S3 object exists
        assertThat(s3.getObjectAsBytes(b -> b.bucket("misc-receipts").key("receipts/" + r.receiptId() + ".json"))
                .asUtf8String()).contains(r.receiptId());
        // Mongo audit document
        assertThat(audit.findByReceiptId(r.receiptId())).hasSize(1);
        // SQS event consumed by the poller
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(registry.counter("receipt_events_consumed").count()).isGreaterThanOrEqualTo(1.0));
        // Prometheus exposition includes our custom metrics
        String prom = http.getForObject("/actuator/prometheus", String.class);
        assertThat(prom).contains("receipts_issued_total").contains("fx_call_seconds");
    }
}

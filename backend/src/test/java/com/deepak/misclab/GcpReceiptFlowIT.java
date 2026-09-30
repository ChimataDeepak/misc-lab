package com.deepak.misclab;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.deepak.misclab.gcp.GcpReceiptSink;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Profile "gcp": POST /api/receipts still does the AWS path (LocalStack) and additionally archives the JSON to
 * Cloud Storage (fake-gcs-server) and publishes ReceiptCreated to Pub/Sub (official emulator). Official Google
 * client libraries throughout; emulators, not a real GCP project.
 */
@Testcontainers
@ActiveProfiles("gcp")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "misclab.poller.enabled=false")
class GcpReceiptFlowIT {
    static final String PROJECT = "misclab-local", TOPIC = "receipt-created", SUB = "receipt-created-test";

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(Service.S3, Service.SQS, Service.DYNAMODB);

    @Container @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    static PubSubEmulatorContainer pubsub = new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> gcs = new GenericContainer<>(DockerImageName.parse("fsouza/fake-gcs-server:1.52.2"))
            .withExposedPorts(4443).withCommand("-scheme", "http", "-port", "4443", "-backend", "memory")
            .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofSeconds(60));

    @RegisterExtension
    static WireMockExtension fxApi = WireMockExtension.newInstance().options(WireMockConfiguration.wireMockConfig().dynamicPort()).build();

    static ManagedChannel channel;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("misclab.aws.endpoint", () -> HostPortReady.awaitHttp(localstack.getEndpoint().toString(), "/_localstack/health"));
        r.add("misclab.aws.region", localstack::getRegion);
        r.add("fx.base-url", fxApi::baseUrl);
        r.add("misclab.gcp.pubsub-emulator-host", pubsub::getEmulatorEndpoint);
        r.add("misclab.gcp.storage-endpoint", () -> HostPortReady.awaitHttp("http://" + gcs.getHost() + ":" + gcs.getMappedPort(4443), "/storage/v1/b"));
    }

    @Autowired TestRestTemplate http;
    @Autowired Storage storage;

    @AfterAll static void close() { if (channel != null) channel.shutdownNow(); }

    @Test
    void receiptGoesToPubSubAndCloudStorage() throws Exception {
        channel = ManagedChannelBuilder.forTarget(pubsub.getEmulatorEndpoint()).usePlaintext().build();
        var ch = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
        var noCreds = NoCredentialsProvider.create();
        // The app created the topic at startup; the test attaches a subscription before publishing anything.
        try (var topics = TopicAdminClient.create(TopicAdminSettings.newBuilder().setTransportChannelProvider(ch).setCredentialsProvider(noCreds).build());
             var subs = SubscriptionAdminClient.create(SubscriptionAdminSettings.newBuilder().setTransportChannelProvider(ch).setCredentialsProvider(noCreds).build())) {
            assertThat(topics.getTopic(TopicName.of(PROJECT, TOPIC)).getName()).endsWith(TOPIC);
            subs.createSubscription(SubscriptionName.of(PROJECT, SUB), TopicName.of(PROJECT, TOPIC), PushConfig.getDefaultInstance(), 10);

            fxApi.stubFor(get(urlPathEqualTo("/rates")).willReturn(okJson("{\"rate\": 0.012}")));
            var res = http.postForEntity("/api/receipts", new Receipt.Request("deepak", new BigDecimal("1000"), "INR"), Receipt.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String id = res.getBody().receiptId();

            // Cloud Storage object written by the official client
            byte[] obj = storage.readAllBytes(BlobId.of("misc-receipts-gcs", GcpReceiptSink.objectName(id)));
            assertThat(new String(obj, StandardCharsets.UTF_8)).contains(id).contains("deepak");

            // Pub/Sub message with attributes
            List<ReceivedMessage> got = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (got.isEmpty() && System.currentTimeMillis() < deadline) {
                PullResponse pr = subs.pull(SubscriptionName.of(PROJECT, SUB).toString(), 10);
                got.addAll(pr.getReceivedMessagesList());
            }
            assertThat(got).hasSize(1);
            var m = got.get(0).getMessage();
            assertThat(m.getAttributesMap()).containsEntry("type", "ReceiptCreated").containsEntry("receiptId", id);
            assertThat(m.getData().toStringUtf8()).contains("\"usdAmount\":12.00");
            subs.acknowledge(SubscriptionName.of(PROJECT, SUB).toString(), List.of(got.get(0).getAckId()));
        }
    }
}

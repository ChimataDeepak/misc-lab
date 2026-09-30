package com.deepak.misclab.gcp;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.NoCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Active only with --spring.profiles.active=gcp. Without it nothing here loads and the app is AWS-only. */
@Configuration
@Profile("gcp")
@EnableConfigurationProperties(GcpProps.class)
public class GcpConfig {

    private static boolean set(String s) { return s != null && !s.isBlank(); }

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel pubsubEmulatorChannel(GcpProps p) {
        // Real GCP: the client builds its own TLS channel; this plaintext channel is only used for the emulator.
        return ManagedChannelBuilder.forTarget(set(p.pubsubEmulatorHost()) ? p.pubsubEmulatorHost() : "localhost:0").usePlaintext().build();
    }

    @Bean(destroyMethod = "shutdown")
    Publisher receiptPublisher(GcpProps p, ManagedChannel pubsubEmulatorChannel) throws IOException {
        TopicName topic = TopicName.of(p.projectId(), p.topic());
        Publisher.Builder b = Publisher.newBuilder(topic);
        if (set(p.pubsubEmulatorHost())) {
            TransportChannelProvider ch = FixedTransportChannelProvider.create(GrpcTransportChannel.create(pubsubEmulatorChannel));
            b.setChannelProvider(ch).setCredentialsProvider(NoCredentialsProvider.create());
            ensureTopic(topic, ch); // emulator starts empty; in real GCP the topic comes from Terraform
        }
        return b.build();
    }

    private static void ensureTopic(TopicName topic, TransportChannelProvider ch) throws IOException {
        try (TopicAdminClient admin = TopicAdminClient.create(TopicAdminSettings.newBuilder()
                .setTransportChannelProvider(ch).setCredentialsProvider(NoCredentialsProvider.create()).build())) {
            try { admin.createTopic(topic); } catch (com.google.api.gax.rpc.AlreadyExistsException ignored) { }
            admin.shutdown();
            try { admin.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    @Bean
    Storage gcsStorage(GcpProps p) {
        StorageOptions.Builder b = StorageOptions.newBuilder().setProjectId(p.projectId());
        if (set(p.storageEndpoint())) b.setHost(p.storageEndpoint()).setCredentials(NoCredentials.getInstance());
        Storage storage = b.build().getService();
        if (set(p.storageEndpoint()) && storage.get(p.bucket()) == null) storage.create(BucketInfo.of(p.bucket()));
        return storage;
    }

    @Bean
    GcpReceiptSink gcpReceiptSink(Publisher receiptPublisher, Storage gcsStorage, GcpProps p) {
        return new GcpReceiptSink(receiptPublisher, gcsStorage, p.bucket());
    }
}

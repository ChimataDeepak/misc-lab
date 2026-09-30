package com.deepak.misclab.gcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * pubsubEmulatorHost / storageEndpoint set = local emulators, no credentials.
 * Both blank = real GCP with Application Default Credentials.
 */
@ConfigurationProperties("misclab.gcp")
public record GcpProps(String projectId, String topic, String bucket, String pubsubEmulatorHost, String storageEndpoint) {}

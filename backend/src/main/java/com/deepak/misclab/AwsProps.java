package com.deepak.misclab;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("misclab.aws")
public record AwsProps(String endpoint, String region, String accessKey, String secretKey,
                       String bucket, String queue, String table, boolean initResources) {}

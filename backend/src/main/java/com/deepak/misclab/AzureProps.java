package com.deepak.misclab;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("misclab.azure")
public record AzureProps(boolean enabled, String connectionString, String container) {}

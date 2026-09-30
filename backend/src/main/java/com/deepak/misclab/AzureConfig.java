package com.deepak.misclab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "misclab.azure.enabled", havingValue = "true")
public class AzureConfig {

    @Bean
    AzureBlobReceiptArchive azureBlobReceiptArchive(AzureProps props, ObjectMapper json) {
        return AzureBlobReceiptArchive.fromConnectionString(props.connectionString(), props.container(), json);
    }
}

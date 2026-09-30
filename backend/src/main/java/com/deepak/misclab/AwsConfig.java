package com.deepak.misclab;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

/** AWS SDK v2 clients pointed at LocalStack. In real AWS you'd drop endpointOverride + static creds. */
@Configuration
public class AwsConfig {

    private static StaticCredentialsProvider creds(AwsProps p) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey()));
    }

    @Bean
    S3Client s3Client(AwsProps p) {
        return S3Client.builder().endpointOverride(URI.create(p.endpoint())).region(Region.of(p.region()))
                .credentialsProvider(creds(p)).forcePathStyle(true).build();
    }

    @Bean
    SqsClient sqsClient(AwsProps p) {
        return SqsClient.builder().endpointOverride(URI.create(p.endpoint())).region(Region.of(p.region()))
                .credentialsProvider(creds(p)).build();
    }

    @Bean
    DynamoDbClient dynamoDbClient(AwsProps p) {
        return DynamoDbClient.builder().endpointOverride(URI.create(p.endpoint())).region(Region.of(p.region()))
                .credentialsProvider(creds(p)).build();
    }

    @Bean
    DynamoDbTable<ReceiptItem> receiptTable(DynamoDbClient client, AwsProps p) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build()
                .table(p.table(), TableSchema.fromBean(ReceiptItem.class));
    }
}

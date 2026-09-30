package com.deepak.misclab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Idempotently creates the bucket, queue and table on LocalStack (infra-as-code would do this in real AWS). */
@Component
@ConditionalOnProperty(name = "misclab.aws.init-resources", havingValue = "true")
public class AwsResourceInitializer {
    private static final Logger log = LoggerFactory.getLogger(AwsResourceInitializer.class);
    private final S3Client s3; private final SqsClient sqs; private final DynamoDbTable<ReceiptItem> table; private final AwsProps p;

    public AwsResourceInitializer(S3Client s3, SqsClient sqs, DynamoDbTable<ReceiptItem> table, AwsProps p) {
        this.s3 = s3; this.sqs = sqs; this.table = table; this.p = p;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try { s3.createBucket(b -> b.bucket(p.bucket())); } catch (BucketAlreadyOwnedByYouException ignored) { }
        sqs.createQueue(b -> b.queueName(p.queue()));  // idempotent
        try { table.createTable(); } catch (ResourceInUseException ignored) { }
        log.info("AWS resources ready: bucket={} queue={} table={}", p.bucket(), p.queue(), p.table());
    }
}

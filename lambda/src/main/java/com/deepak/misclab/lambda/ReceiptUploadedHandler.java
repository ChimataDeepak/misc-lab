package com.deepak.misclab.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

/**
 * Triggered when a receipt JSON lands in S3. Handler string for AWS:
 * com.deepak.misclab.lambda.ReceiptUploadedHandler::handleRequest
 */
public class ReceiptUploadedHandler implements RequestHandler<S3Event, String> {

    @Override
    public String handleRequest(S3Event event, Context context) {
        var records = event.getRecords();
        long totalBytes = records.stream().mapToLong(r -> r.getS3().getObject().getSizeAsLong()).sum();
        String keys = String.join(",", records.stream().map(r -> r.getS3().getObject().getKey()).toList());
        String summary = "processed " + records.size() + " object(s), " + totalBytes + " bytes: " + keys;
        if (context != null) context.getLogger().log(summary);
        return summary;
    }
}

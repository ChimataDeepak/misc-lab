package com.deepak.misclab;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Azure Blob Storage archive for receipt JSON. Point the connection string at Azurite locally
 * (or a real storage account in Azure). Parallel to the AWS S3 put in {@link ReceiptService}.
 */
public class AzureBlobReceiptArchive {
    private final BlobContainerClient container;
    private final ObjectMapper json;

    public AzureBlobReceiptArchive(BlobContainerClient container, ObjectMapper json) {
        this.container = container;
        this.json = json;
    }

    public static AzureBlobReceiptArchive fromConnectionString(String connectionString, String containerName,
                                                               ObjectMapper json) {
        BlobContainerClient client = new BlobContainerClientBuilder()
                .connectionString(connectionString)
                .containerName(containerName)
                .buildClient();
        client.createIfNotExists();
        return new AzureBlobReceiptArchive(client, json);
    }

    public void put(Receipt receipt) {
        container.getBlobClient(key(receipt.receiptId()))
                .upload(com.azure.core.util.BinaryData.fromString(toJson(receipt)), true);
    }

    public String getJson(String receiptId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        container.getBlobClient(key(receiptId)).downloadStream(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    static String key(String receiptId) {
        return "receipts/" + receiptId + ".json";
    }

    private String toJson(Receipt receipt) {
        try {
            return json.writeValueAsString(receipt);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

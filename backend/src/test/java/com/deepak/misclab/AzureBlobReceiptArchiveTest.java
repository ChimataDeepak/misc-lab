package com.deepak.misclab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AzureBlobReceiptArchiveTest {

    @Test
    void putUploadsJsonToReceiptsPrefix() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        when(container.getBlobClient("receipts/r1.json")).thenReturn(blob);

        Receipt r = new Receipt("r1", "deepak", new BigDecimal("10"), "USD", new BigDecimal("10.00"), Instant.parse("2026-09-30T00:00:00Z"));
        new AzureBlobReceiptArchive(container, new ObjectMapper().findAndRegisterModules()).put(r);

        verify(blob).upload(any(BinaryData.class), eq(true));
        assertThat(AzureBlobReceiptArchive.key("r1")).isEqualTo("receipts/r1.json");
    }
}

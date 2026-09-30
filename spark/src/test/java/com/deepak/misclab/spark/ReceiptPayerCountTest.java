package com.deepak.misclab.spark;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReceiptPayerCountTest {

    @Test
    void countsPayersFromLocalText() throws Exception {
        Path f = Files.createTempFile("receipts", ".txt");
        Files.writeString(f, "deepak 10\npriya 5\ndeepak 3\n");
        Map<String, Long> counts = ReceiptPayerCount.countPayers(f.toString());
        assertEquals(2L, counts.get("deepak"));
        assertEquals(1L, counts.get("priya"));
        assertEquals(2, counts.size());
    }
}

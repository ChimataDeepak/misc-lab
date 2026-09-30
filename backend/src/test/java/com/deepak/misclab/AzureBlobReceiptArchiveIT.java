package com.deepak.misclab;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Azure Blob SDK against Azurite (the local Azure Storage emulator). Needs Docker.
 * Honest claim: emulator Blob API, not a paid Azure subscription or AKS.
 */
@Testcontainers
class AzureBlobReceiptArchiveIT {

    /** Microsoft-published Azurite account key (not a personal secret). */ // gitleaks:allow
    private static final String ACCOUNT_KEY = new String(java.util.Base64.getDecoder().decode(
            "RWJ5OHZkTTAyeE5PY3FGbHFVd0pQTGxtRXRsQ0RYSjFPVXpGVDUwdVNSWjZJRnN1RnEyVVZFckN6NEk2dHEvSzFTWkZQVE90ci9LQkhCZWtzb0dNR3c9PQ=="));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> azurite = new GenericContainer<>(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.33.0"))
            .withExposedPorts(10000)
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--blobPort", "10000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60));

    @Test
    void putThenGetReceiptJson() {
        String endpoint = "http://" + azurite.getHost() + ":" + azurite.getMappedPort(10000) + "/devstoreaccount1";
        String conn = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=" + ACCOUNT_KEY
                + ";BlobEndpoint=" + endpoint + ";";
        AzureBlobReceiptArchive archive = AzureBlobReceiptArchive.fromConnectionString(
                conn, "receipts", new ObjectMapper().findAndRegisterModules());
        Receipt r = new Receipt("az-1", "deepak", new BigDecimal("100"), "INR", new BigDecimal("1.20"), Instant.parse("2026-09-30T12:00:00Z"));

        archive.put(r);

        assertThat(archive.getJson("az-1")).contains("az-1").contains("deepak");
    }
}

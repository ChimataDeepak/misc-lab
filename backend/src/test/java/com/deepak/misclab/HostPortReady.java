package com.deepak.misclab;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Rancher Desktop forwards container ports to the Mac asynchronously: Testcontainers can report LocalStack as started
 * (log line seen inside the container) a moment before 127.0.0.1:&lt;mapped port&gt; accepts connections, which made
 * ReceiptFlowIT fail ~1 in 3 runs with "Connection refused" during Spring startup. Poll the health URL from the
 * host side first.
 */
final class HostPortReady {
    private HostPortReady() {}

    static String awaitHttp(String baseUrl, String healthPath) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try {
                var res = client.send(HttpRequest.newBuilder(URI.create(baseUrl + healthPath)).timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.discarding());
                if (res.statusCode() < 500) return baseUrl;
            } catch (java.io.IOException ignored) {
                // not forwarded yet
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            if (System.nanoTime() > deadline) throw new IllegalStateException(baseUrl + healthPath + " not reachable from host after 30s");
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }
    }
}

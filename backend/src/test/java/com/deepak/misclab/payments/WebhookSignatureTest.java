package com.deepak.misclab.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {
    static final String SECRET = "whsec_test";
    static final long T = 1_700_000_000L;
    static final String BODY = "{\"id\":\"evt_1\"}";

    WebhookVerifier verifierAt(long unixSeconds) {
        return new WebhookVerifier(SECRET, Duration.ofMinutes(5), Clock.fixed(Instant.ofEpochSecond(unixSeconds), ZoneOffset.UTC));
    }

    /** Fixed vector shared with go-verify/ and python (hmac.new(...).hexdigest()) so all three agree on the scheme. */
    @Test void knownVector() {
        assertThat(WebhookSigner.header(SECRET, T, BODY))
                .isEqualTo("t=1700000000,v1=c89214b5b5da833daed6f0b8c5bb6bd58cea9022bd80ccc78230f3942d632925");
    }

    @Test void validSignatureAccepted() {
        verifierAt(T + 10).verify(WebhookSigner.header(SECRET, T, BODY), BODY, "evt_1");
    }

    @Test void tamperedBodyRejected() {
        String h = WebhookSigner.header(SECRET, T, BODY);
        assertThatThrownBy(() -> verifierAt(T).verify(h, "{\"id\":\"evt_2\"}", "evt_1")).hasMessage("signature mismatch");
    }

    @Test void wrongSecretRejected() {
        String h = WebhookSigner.header("other", T, BODY);
        assertThatThrownBy(() -> verifierAt(T).verify(h, BODY, "evt_1")).hasMessage("signature mismatch");
    }

    @Test void changedTimestampRejected() {
        String h = WebhookSigner.header(SECRET, T, BODY).replace("t=" + T, "t=" + (T + 60));
        assertThatThrownBy(() -> verifierAt(T + 60).verify(h, BODY, "evt_1")).hasMessage("signature mismatch");
    }

    @Test void staleTimestampRejected() {
        String h = WebhookSigner.header(SECRET, T, BODY);
        assertThatThrownBy(() -> verifierAt(T + 301).verify(h, BODY, "evt_1")).hasMessage("timestamp outside tolerance");
        assertThatThrownBy(() -> verifierAt(T - 301).verify(h, BODY, "evt_1")).hasMessage("timestamp outside tolerance");
    }

    @Test void replayWithinWindowRejected() {
        WebhookVerifier v = verifierAt(T + 1);
        String h = WebhookSigner.header(SECRET, T, BODY);
        v.verify(h, BODY, "evt_1");
        assertThatThrownBy(() -> v.verify(h, BODY, "evt_1")).hasMessage("replayed event evt_1");
    }

    @Test void malformedHeaderRejected() {
        assertThatThrownBy(() -> verifierAt(T).verify(null, BODY, "e")).hasMessage("malformed signature header");
        assertThatThrownBy(() -> verifierAt(T).verify("v1=abc", BODY, "e")).hasMessage("malformed signature header");
        assertThatThrownBy(() -> verifierAt(T).verify("t=x,v1=abc", BODY, "e")).hasMessage("malformed signature header");
    }
}

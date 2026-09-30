package com.deepak.misclab.payments;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs outbound webhooks. Header: {@code Misclab-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256>} where the MAC is
 * over {@code "<t>.<raw body>"}. Putting the timestamp inside the MAC stops an attacker from replaying an old body
 * with a fresh timestamp. Same scheme is implemented in go-verify/.
 */
public final class WebhookSigner {
    public static final String HEADER = "Misclab-Signature";

    private WebhookSigner() {}

    public static String header(String secret, long unixSeconds, String body) {
        return "t=" + unixSeconds + ",v1=" + hmacHex(secret, unixSeconds + "." + body);
    }

    static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}

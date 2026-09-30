package com.deepak.misclab;

import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OidcReceiptController {

    @GetMapping("/api/oidc/me")
    public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "sub", jwt.getSubject(),
                "iss", String.valueOf(jwt.getIssuer()),
                "aud", jwt.getAudience().isEmpty() ? "" : jwt.getAudience().get(0));
    }
}

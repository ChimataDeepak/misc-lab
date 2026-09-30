package com.deepak.misclab;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2/OIDC resource server on {@code /api/oidc/**} only. JWT with issuer + audience checks
 * (HMAC stand-in for an IdP). Existing REST/GraphQL stays open. Not Okta/Auth0 or auth-code login.
 */
@Configuration
@EnableWebSecurity
public class OidcResourceServerConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${misclab.oidc.hmac-secret}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain oidcChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/oidc/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .csrf(c -> c.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain openChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable());
        return http.build();
    }
}

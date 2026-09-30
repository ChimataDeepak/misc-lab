package com.deepak.misclab;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"misclab.aws.init-resources=false", "misclab.poller.enabled=false"})
@AutoConfigureMockMvc
class OidcReceiptControllerTest {

    @Autowired MockMvc mvc;
    @Value("${misclab.oidc.hmac-secret}") String secret;

    @Test
    void oidcMeRequiresJwt() throws Exception {
        mvc.perform(get("/api/oidc/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void oidcMeReturnsIssuerAudienceFromJwt() throws Exception {
        String token = signedJwt();
        mvc.perform(get("/api/oidc/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("deepak"))
                .andExpect(jsonPath("$.iss").value("https://issuer.misclab.test"))
                .andExpect(jsonPath("$.aud").value("misc-lab"));
    }

    private String signedJwt() throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .subject("deepak")
                .issuer("https://issuer.misclab.test")
                .audience(List.of("misc-lab"))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .issueTime(Date.from(Instant.now()))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes()));
        return jwt.serialize();
    }
}

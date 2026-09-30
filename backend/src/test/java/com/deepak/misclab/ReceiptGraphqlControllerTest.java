package com.deepak.misclab;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(ReceiptGraphqlController.class)
class ReceiptGraphqlControllerTest {

    @Autowired GraphQlTester graphQl;
    @MockitoBean ReceiptService service;

    @Test
    void receiptQueryReturnsFields() {
        when(service.get("r1")).thenReturn(new Receipt(
                "r1", "deepak", new BigDecimal("1000"), "INR",
                new BigDecimal("12.00"), Instant.parse("2026-09-30T00:00:00Z")));

        graphQl.document("{ receipt(id: \"r1\") { payer usdAmount currency } }")
                .execute()
                .path("receipt.payer").entity(String.class).isEqualTo("deepak")
                .path("receipt.usdAmount").entity(String.class).isEqualTo("12.00")
                .path("receipt.currency").entity(String.class).isEqualTo("INR");
    }

    @Test
    void missingReceiptIsNull() {
        when(service.get("missing")).thenReturn(null);
        graphQl.document("{ receipt(id: \"missing\") { payer } }")
                .execute()
                .path("receipt").valueIsNull();
    }
}

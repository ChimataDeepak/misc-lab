package com.deepak.misclab.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepak.misclab.OidcResourceServerConfig;
import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptController;
import com.deepak.misclab.ReceiptService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Jakarta Bean Validation: plain API (no Spring) + the same rules enforced by Spring MVC on POST /api/receipts. */
@WebMvcTest(ReceiptController.class)
@Import(OidcResourceServerConfig.class)
class ReceiptRequestValidationTest {
    static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Autowired MockMvc mvc;
    @MockitoBean ReceiptService service;

    static Set<String> violations(Receipt.Request r) {
        return validator.validate(r).stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).collect(Collectors.toSet());
    }

    @Test void isoCurrencyAcceptsRealCodesOnly() {
        assertThat(violations(new Receipt.Request("deepak", new BigDecimal("10"), "INR"))).isEmpty();
        assertThat(violations(new Receipt.Request("deepak", new BigDecimal("10"), "XYZ")))
                .containsExactly("currency: must be an ISO-4217 currency code like USD or INR");
        assertThat(violations(new Receipt.Request("deepak", new BigDecimal("10"), "usd"))).hasSize(1);   // case-sensitive
    }

    @Test void standardConstraintsOnTheSameRecord() {
        Set<ConstraintViolation<Receipt.Request>> v = validator.validate(new Receipt.Request(" ", new BigDecimal("-1"), null));
        assertThat(v).extracting(c -> c.getPropertyPath().toString()).containsExactlyInAnyOrder("payer", "amount", "currency");
    }

    @Test void invalidRequestIsRejectedWith400BeforeTheService() throws Exception {
        mvc.perform(post("/api/receipts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payer\":\"deepak\",\"amount\":10,\"currency\":\"ABC\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any());
    }

    @Test void validRequestReachesTheService() throws Exception {
        mvc.perform(post("/api/receipts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payer\":\"deepak\",\"amount\":10,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated());
        verify(service).create(new Receipt.Request("deepak", new BigDecimal("10"), "USD"));
    }
}

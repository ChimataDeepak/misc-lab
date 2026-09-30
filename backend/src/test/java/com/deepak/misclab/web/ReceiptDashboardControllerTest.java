package com.deepak.misclab.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.deepak.misclab.OidcResourceServerConfig;
import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Renders the real Thymeleaf template through Spring MVC and checks the HTML that a browser would get. */
@WebMvcTest(ReceiptDashboardController.class)
@Import(OidcResourceServerConfig.class)   // the app's real security chains (dashboard is on the open chain)
class ReceiptDashboardControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReceiptService receipts;

    static Receipt r(String id, String payer, String amount, String ccy, String usd, String at) {
        return new Receipt(id, payer, new BigDecimal(amount), ccy, new BigDecimal(usd), Instant.parse(at));
    }

    @Test void rendersOneRowPerReceiptNewestFirstWithTotals() throws Exception {
        when(receipts.list(anyInt())).thenReturn(List.of(
                r("rc-old", "asha", "1000", "INR", "12.00", "2026-09-29T09:00:00Z"),
                r("rc-new", "deepak", "250.50", "EUR", "270.54", "2026-09-30T09:00:00Z"),
                r("rc-mid", "ravi", "2000", "INR", "24.00", "2026-09-29T18:00:00Z")));

        String html = mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("href=\"/css/dashboard.css\"")))
                .andExpect(content().string(containsString("id=\"usd-total\">306.54<")))   // 12 + 270.54 + 24
                .andExpect(content().string(containsString(">36.00<")))                   // INR subtotal card
                .andReturn().getResponse().getContentAsString();

        var ids = Pattern.compile("data-receipt-id=\"([^\"]+)\"").matcher(html).results().map(m -> m.group(1)).toList();
        assertThat(ids).containsExactly("rc-new", "rc-mid", "rc-old");
        assertThat(html).contains("<td data-label=\"Amount\" class=\"num\">250.50 EUR</td>");
    }

    @Test void payerNameIsHtmlEscaped() throws Exception {
        when(receipts.list(anyInt())).thenReturn(List.of(
                r("rc-x", "<script>alert(1)</script>", "1", "USD", "1.00", "2026-09-30T09:00:00Z")));
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test void emptyStateAndCssIsServed() throws Exception {
        when(receipts.list(anyInt())).thenReturn(List.of());
        mvc.perform(get("/dashboard")).andExpect(content().string(containsString("No receipts yet.")))
                .andExpect(content().string(not(containsString("<table"))));
        mvc.perform(get("/css/dashboard.css")).andExpect(status().isOk())
                .andExpect(content().string(containsString("@media (max-width: 640px)")));
    }
}

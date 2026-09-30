package com.deepak.misclab.web;

import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Server-rendered receipts dashboard (Thymeleaf). No JS needed: the HTML arrives complete, so it works everywhere. */
@Controller
public class ReceiptDashboardController {
    private final ReceiptService receipts;

    public ReceiptDashboardController(ReceiptService receipts) { this.receipts = receipts; }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "50") int limit, Model model) {
        List<Receipt> rows = receipts.list(Math.max(1, Math.min(limit, 200))).stream()
                .sorted(Comparator.comparing(Receipt::createdAt).reversed()).toList();
        Map<String, BigDecimal> usdByCurrency = new TreeMap<>();
        rows.forEach(r -> usdByCurrency.merge(r.currency(), r.usdAmount(), BigDecimal::add));
        model.addAttribute("receipts", rows);
        model.addAttribute("usdByCurrency", usdByCurrency);
        model.addAttribute("usdTotal", rows.stream().map(Receipt::usdAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "dashboard";
    }
}

package com.deepak.misclab.statement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statements")
public class StatementController {
    private final StatementService service;

    public StatementController(StatementService service) { this.service = service; }

    @GetMapping("/{payerId}")
    public Statement statement(@PathVariable long payerId, @RequestParam(defaultValue = "20") int limit) {
        return service.statement(payerId, limit);
    }

    public record NewReceipt(@NotBlank @Size(max = 100) String merchant, @NotBlank @Size(min = 3, max = 3) String currency,
                             @NotEmpty List<Statement.Line> lines) {}

    @PostMapping("/{payerId}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> add(@PathVariable long payerId, @Valid @RequestBody NewReceipt body) {
        return Map.of("id", service.addReceipt(payerId, body.merchant(), body.currency(), body.lines()));
    }
}

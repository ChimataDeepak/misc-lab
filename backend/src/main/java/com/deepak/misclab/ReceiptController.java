package com.deepak.misclab;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receipts")
@CrossOrigin(origins = "http://localhost:5173")
public class ReceiptController {
    private final ReceiptService service;

    public ReceiptController(ReceiptService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Receipt create(@jakarta.validation.Valid @RequestBody Receipt.Request req) { return service.create(req); }

    @PostMapping("/batch")
    public List<Receipt> batch(@RequestBody List<Receipt.Request> reqs) { return service.convertBatch(reqs); }

    @GetMapping
    public List<Receipt> list(@RequestParam(defaultValue = "100") int limit) { return service.list(Math.max(1, Math.min(limit, 500))); }

    /** A storage write failed: 503 so clients know to retry (a retry creates a new receipt; see README). */
    @ExceptionHandler(ReceiptService.ReceiptWriteException.class)
    org.springframework.http.ProblemDetail writeFailed(ReceiptService.ReceiptWriteException e) {
        var pd = org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        pd.setProperty("failedStores", e.failedStores());
        return pd;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Receipt> get(@PathVariable String id) {
        Receipt r = service.get(id);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }
}

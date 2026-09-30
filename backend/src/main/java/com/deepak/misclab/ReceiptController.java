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
    public Receipt create(@RequestBody Receipt.Request req) { return service.create(req); }

    @PostMapping("/batch")
    public List<Receipt> batch(@RequestBody List<Receipt.Request> reqs) { return service.convertBatch(reqs); }

    @GetMapping("/{id}")
    public ResponseEntity<Receipt> get(@PathVariable String id) {
        Receipt r = service.get(id);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }
}

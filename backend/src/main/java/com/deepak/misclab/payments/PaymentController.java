package com.deepak.misclab.payments;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    public record CreateRequest(String receiptId, long amountMinor, String currency) {}
    public record RefundRequest(long amountMinor) {}

    private final PaymentService payments;
    private final WebhookPublisher webhooks;
    private final WebhookVerifier verifier;

    public PaymentController(PaymentService payments, WebhookPublisher webhooks, WebhookVerifier verifier) {
        this.payments = payments; this.webhooks = webhooks; this.verifier = verifier;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentIntent create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                @RequestBody CreateRequest req) {
        return payments.create(req.receiptId(), new Money(req.amountMinor(), req.currency()), key);
    }

    @GetMapping("/{id}") public PaymentIntent get(@PathVariable String id) { return payments.get(id); }

    @PostMapping("/{id}/authorize") public PaymentIntent authorize(@PathVariable String id) { return payments.authorize(id); }

    @PostMapping("/{id}/void") public PaymentIntent voidIt(@PathVariable String id) { return payments.voidIt(id); }

    @PostMapping("/{id}/capture")
    public PaymentIntent capture(@PathVariable String id, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return payments.capture(id, key);
    }

    @PostMapping("/{id}/refund")
    public PaymentIntent refund(@PathVariable String id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                @RequestBody RefundRequest req) {
        return payments.refund(id, req.amountMinor(), key);
    }

    /** Lab-only view of what the service has signed and sent. */
    @GetMapping("/webhooks/outbox")
    public List<Map<String, String>> outbox() {
        return webhooks.outbox().stream()
                .map(s -> Map.of("id", s.eventId(), "type", s.type(), "paymentId", s.paymentId(), "signature", s.signatureHeader())).toList();
    }

    /** Receiver endpoint: shows the verify side (signature, timestamp window, replay). */
    @PostMapping("/webhooks/receive")
    public Map<String, String> receive(@RequestHeader(WebhookSigner.HEADER) String sig,
                                       @RequestHeader("Misclab-Event-Id") String eventId, @RequestBody String body) {
        verifier.verify(sig, body, eventId);
        return Map.of("status", "accepted", "eventId", eventId);
    }

    @ExceptionHandler(PaymentIntent.IllegalTransitionException.class)
    ProblemDetail conflict(RuntimeException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()); }

    @ExceptionHandler(IdempotencyStore.KeyReuseException.class)
    ProblemDetail reuse(RuntimeException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }

    @ExceptionHandler(PaymentService.NotFound.class)
    ProblemDetail notFound(RuntimeException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler(WebhookVerifier.Rejected.class)
    ProblemDetail rejected(RuntimeException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage()); }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ProblemDetail stale(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "payment was modified concurrently, retry");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail bad(RuntimeException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()); }
}

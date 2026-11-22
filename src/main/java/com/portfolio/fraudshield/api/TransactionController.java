package com.portfolio.fraudshield.api;

import com.portfolio.fraudshield.domain.TransactionEvent;
import com.portfolio.fraudshield.domain.TransactionRecord;
import com.portfolio.fraudshield.domain.TransactionStatus;
import com.portfolio.fraudshield.service.IngestResult;
import com.portfolio.fraudshield.service.TransactionIngestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionIngestService service;

    public TransactionController(TransactionIngestService service) {
        this.service = service;
    }

    public record IngestRequest(
            @NotBlank @Size(max = 80) String eventId,
            @NotBlank @Size(max = 100) String customerId,
            @NotBlank @Size(max = 100) String accountId,
            @Size(max = 100) String merchantId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull TransactionStatus status,
            @NotNull Instant eventTime,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Pattern(regexp = "[A-Z]{2}") String countryCode) {

        TransactionEvent toEvent() {
            return new TransactionEvent(eventId, customerId, accountId, merchantId, amount,
                    currency, status, eventTime, latitude, longitude, countryCode);
        }
    }

    public record TransactionView(
            String eventId,
            String customerId,
            String accountId,
            String merchantId,
            BigDecimal amount,
            String currency,
            TransactionStatus status,
            Instant eventTime,
            Double latitude,
            Double longitude,
            String countryCode,
            boolean labelledFraud) {

        static TransactionView from(TransactionRecord r) {
            return new TransactionView(r.getEventId(), r.getCustomerId(), r.getAccountId(),
                    r.getMerchantId(), r.getAmount(), r.getCurrency(), r.getStatus(),
                    r.getEventTime(), r.getLatitude(), r.getLongitude(), r.getCountryCode(),
                    r.isLabelledFraud());
        }
    }

    /** {@code outcome} is ACCEPTED for a new event and DUPLICATE when the eventId was already stored. */
    public record IngestResponse(String outcome, TransactionView transaction) {
    }

    /** 202 Accepted for a new event, 200 OK for an idempotent replay of a known eventId. */
    @PostMapping
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        IngestResult result = service.ingest(request.toEvent());
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        String outcome = result.duplicate() ? "DUPLICATE" : "ACCEPTED";
        return ResponseEntity.status(status)
                .body(new IngestResponse(outcome, TransactionView.from(result.record())));
    }

    @GetMapping("/{eventId}")
    public TransactionView get(@PathVariable String eventId) {
        return service.find(eventId)
                .map(TransactionView::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No transaction with eventId " + eventId));
    }

    /** Latest (up to 100) transactions of a customer, newest first. */
    @GetMapping
    public List<TransactionView> recent(@RequestParam String customerId) {
        return service.recentForCustomer(customerId).stream().map(TransactionView::from).toList();
    }
}

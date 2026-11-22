package com.portfolio.fraudshield.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.fraudshield.domain.TransactionEvent;
import com.portfolio.fraudshield.domain.TransactionRecord;
import com.portfolio.fraudshield.domain.TransactionStatus;
import com.portfolio.fraudshield.service.IngestResult;
import com.portfolio.fraudshield.service.TransactionIngestService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String VALID_BODY = """
            {
              "eventId": "evt-1",
              "customerId": "cust-1",
              "accountId": "acct-1",
              "merchantId": "m-1",
              "amount": 42.50,
              "currency": "USD",
              "status": "APPROVED",
              "eventTime": "2026-01-01T12:00:00Z",
              "latitude": 37.77,
              "longitude": -122.42,
              "countryCode": "US"
            }
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TransactionIngestService service;

    private static TransactionRecord record() {
        return new TransactionRecord(new TransactionEvent("evt-1", "cust-1", "acct-1", "m-1",
                new BigDecimal("42.50"), "USD", TransactionStatus.APPROVED,
                Instant.parse("2026-01-01T12:00:00Z"), 37.77, -122.42, "US"));
    }

    @Test
    void newTransactionIsAccepted() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestResult(record(), false));

        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.transaction.eventId").value("evt-1"));
    }

    @Test
    void replayOfKnownEventIdIsIdempotentAndReturnsOk() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestResult(record(), true));

        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
    }

    @Test
    void concurrentDuplicateSurfacesAsConflict() throws Exception {
        when(service.ingest(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate eventId"));
    }

    @Test
    void invalidCurrencyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"USD\"", "\"usd\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonPositiveAmountIsRejected() throws Exception {
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("42.50", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingEventIdIsRejected() throws Exception {
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"eventId\": \"evt-1\",", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outOfRangeLatitudeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("37.77", "91.0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsStoredTransaction() throws Exception {
        when(service.find("evt-1")).thenReturn(Optional.of(record()));

        mvc.perform(get("/api/v1/transactions/evt-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("cust-1"))
                .andExpect(jsonPath("$.labelledFraud").value(false));
    }

    @Test
    void getUnknownTransactionIsNotFound() throws Exception {
        when(service.find("nope")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/transactions/nope")).andExpect(status().isNotFound());
    }

    @Test
    void recentListsTransactionsForCustomer() throws Exception {
        when(service.recentForCustomer("cust-1")).thenReturn(List.of(record()));

        mvc.perform(get("/api/v1/transactions").param("customerId", "cust-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-1"));
    }
}

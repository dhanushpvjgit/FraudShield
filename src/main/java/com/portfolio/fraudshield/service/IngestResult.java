package com.portfolio.fraudshield.service;

import com.portfolio.fraudshield.domain.TransactionRecord;

/** Outcome of an ingest call: the stored record and whether it was already present. */
public record IngestResult(TransactionRecord record, boolean duplicate) {
}

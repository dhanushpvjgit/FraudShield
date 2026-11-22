package com.portfolio.fraudshield.repository;

import com.portfolio.fraudshield.domain.TransactionRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionRecord, String> {

    List<TransactionRecord> findTop100ByCustomerIdOrderByEventTimeDesc(String customerId);
}

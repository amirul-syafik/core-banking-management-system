package com.cbms.repository;

import com.cbms.entity.BankLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankLedgerEntryRepository
        extends JpaRepository<BankLedgerEntry, Long> {

    List<BankLedgerEntry> findByTxnIdOrderByEntryIdAsc(Long txnId);
}
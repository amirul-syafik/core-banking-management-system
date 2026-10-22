package com.cbms.repository;

import com.cbms.entity.BankLedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankLedgerAccountRepository
        extends JpaRepository<BankLedgerAccount, Long> {

    Optional<BankLedgerAccount> findByGlCd(String glCd);
}
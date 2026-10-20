package com.cbms.repository;

import com.cbms.entity.BankAccountBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface BankAccountBalanceRepository
        extends JpaRepository<BankAccountBalance, Long> {

    List<BankAccountBalance> findByAcctIdIn(Collection<Long> accountIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BankAccountBalance b WHERE b.acctId = :acctId")
    Optional<BankAccountBalance> findByIdForUpdate(
            @Param("acctId") Long acctId
    );
}
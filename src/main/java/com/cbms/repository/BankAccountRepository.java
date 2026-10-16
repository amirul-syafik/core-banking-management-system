package com.cbms.repository;

import com.cbms.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository
        extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByCustIdOrderByAcctIdDesc(Long custId);

    Optional<BankAccount> findByAcctNo(String acctNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccount a WHERE a.acctId = :acctId")
    Optional<BankAccount> findByIdForUpdate(@Param("acctId") Long acctId);
}
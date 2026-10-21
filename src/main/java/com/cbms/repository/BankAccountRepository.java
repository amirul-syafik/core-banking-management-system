package com.cbms.repository;

import com.cbms.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository
        extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByCustIdOrderByAcctIdDesc(Long custId);

    @Query(value = """
        SELECT a FROM BankAccount a, Customer c
        WHERE a.custId = c.custId
          AND (:accountNo IS NULL OR LOWER(a.acctNo) LIKE LOWER(CONCAT('%', :accountNo, '%')))
          AND (:cifNo IS NULL OR LOWER(c.cifNo) LIKE LOWER(CONCAT('%', :cifNo, '%')))
          AND (:status IS NULL OR a.acctSts = :status)
        """, countQuery = """
        SELECT COUNT(a) FROM BankAccount a, Customer c
        WHERE a.custId = c.custId
          AND (:accountNo IS NULL OR LOWER(a.acctNo) LIKE LOWER(CONCAT('%', :accountNo, '%')))
          AND (:cifNo IS NULL OR LOWER(c.cifNo) LIKE LOWER(CONCAT('%', :cifNo, '%')))
          AND (:status IS NULL OR a.acctSts = :status)
        """)
    Page<BankAccount> searchAccounts(@Param("accountNo") String accountNo,
                                     @Param("cifNo") String cifNo,
                                     @Param("status") String status,
                                     Pageable pageable);

    Optional<BankAccount> findByAcctNo(String acctNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccount a WHERE a.acctId = :acctId")
    Optional<BankAccount> findByIdForUpdate(@Param("acctId") Long acctId);
}
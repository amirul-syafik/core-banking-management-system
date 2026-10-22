package com.cbms.repository;

import com.cbms.entity.BankBaseRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BankBaseRateRepository
        extends JpaRepository<BankBaseRate, Long> {

    boolean existsByRateCdAndEffDt(String rateCd, LocalDate effDt);

    Optional<BankBaseRate>
    findFirstByRateCdAndEffDtLessThanEqualAndRateStsOrderByEffDtDesc(
            String rateCd, LocalDate date, String status);

    List<BankBaseRate> findByRateCdOrderByEffDtDesc(String rateCd);
}
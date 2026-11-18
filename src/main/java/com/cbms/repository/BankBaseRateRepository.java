package com.cbms.repository;

import com.cbms.entity.BankBaseRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(value = """
        SELECT DISTINCT ON (rate_cd) * FROM public.bank_base_rate
        ORDER BY rate_cd, eff_dt DESC, base_rate_id DESC
        """, nativeQuery = true)
    List<BankBaseRate> findLatestPerCode();

    @Query(value = """
        SELECT DISTINCT ON (rate_cd) * FROM public.bank_base_rate
        WHERE eff_dt <= :asOf
        ORDER BY rate_cd, eff_dt DESC, base_rate_id DESC
        """, nativeQuery = true)
    List<BankBaseRate> findLatestPerCodeAsOf(@Param("asOf") LocalDate asOf);
}
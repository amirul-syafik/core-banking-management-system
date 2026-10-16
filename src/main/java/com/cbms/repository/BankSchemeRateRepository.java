package com.cbms.repository;

import com.cbms.entity.BankSchemeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BankSchemeRateRepository
        extends JpaRepository<BankSchemeRate, Long> {

    boolean existsBySchmIdAndEffDt(Long schmId, LocalDate effDt);

    Optional<BankSchemeRate>
    findFirstBySchmIdAndEffDtLessThanEqualAndRateStsOrderByEffDtDesc(
            Long schmId, LocalDate date, String status);

    List<BankSchemeRate> findBySchmIdOrderByEffDtDesc(Long schmId);
}
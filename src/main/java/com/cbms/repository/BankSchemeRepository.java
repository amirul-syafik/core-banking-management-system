package com.cbms.repository;

import com.cbms.entity.BankScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankSchemeRepository
        extends JpaRepository<BankScheme, Long> {

    boolean existsByProdIdAndSchmCd(Long prodId, String schmCd);

    List<BankScheme> findByProdIdOrderBySchmCdAsc(Long prodId);

    List<BankScheme> findAllByOrderByProdIdAscSchmCdAsc();
}

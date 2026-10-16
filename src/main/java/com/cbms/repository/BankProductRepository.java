package com.cbms.repository;

import com.cbms.entity.BankProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankProductRepository
        extends JpaRepository<BankProduct, Long> {

    Optional<BankProduct> findByProdCd(String prodCd);

    boolean existsByProdCd(String prodCd);

    List<BankProduct> findByProdCatOrderByProdCdAsc(String prodCat);

    List<BankProduct> findByProdStsOrderByProdCdAsc(String prodSts);

    List<BankProduct> findAllByOrderByProdCdAsc();
}

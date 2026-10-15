package com.cbms.repository;

import com.cbms.entity.CorporateAuthorizedPerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;

public interface CorporateAuthorizedPersonRepository
        extends JpaRepository<CorporateAuthorizedPerson, Long> {

    List<CorporateAuthorizedPerson> findByCorpCustId(Long corpCustId);

    boolean existsByCorpCustIdAndAuthCustIdAndAuthTypAndEffDt(
            Long corpCustId,
            Long authCustId,
            String authTyp,
            LocalDate effDt
    );
}

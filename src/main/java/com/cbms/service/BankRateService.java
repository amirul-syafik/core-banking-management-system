
package com.cbms.service;

import com.cbms.dto.BankBaseRateCreateRequest;
import com.cbms.dto.BankBaseRateCodeOption;
import com.cbms.dto.BankSchemeRateCreateRequest;
import com.cbms.entity.BankBaseRate;
import com.cbms.entity.BankScheme;
import com.cbms.entity.BankSchemeRate;
import com.cbms.repository.BankBaseRateRepository;
import com.cbms.repository.BankSchemeRepository;
import com.cbms.repository.BankSchemeRateRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class BankRateService {

    private final BankBaseRateRepository baseRateRepository;
    private final BankSchemeRateRepository schemeRateRepository;
    private final BankSchemeRepository schemeRepository;

    public BankRateService(
            BankBaseRateRepository baseRateRepository,
            BankSchemeRateRepository schemeRateRepository,
            BankSchemeRepository schemeRepository
    ) {
        this.baseRateRepository = baseRateRepository;
        this.schemeRateRepository = schemeRateRepository;
        this.schemeRepository = schemeRepository;
    }

    @Transactional
    public BankBaseRate createBaseRate(
            BankBaseRateCreateRequest request,
            Authentication authentication
    ) {
        if (baseRateRepository.existsByRateCdAndEffDt(
                request.rateCd(), request.effDt())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Base rate already exists for this effective date"
            );
        }

        BankBaseRate rate = new BankBaseRate();
        rate.setRateCd(request.rateCd());
        rate.setRateNm(request.rateNm().trim());
        rate.setCcyCd(request.ccyCd());
        rate.setRatePct(request.ratePct());
        rate.setEffDt(request.effDt());
        rate.setRateSts("ACTIVE");
        rate.setCrtBy(authentication.getName());

        return baseRateRepository.saveAndFlush(rate);
    }

    @Transactional
    public BankSchemeRate createSchemeRate(
            BankSchemeRateCreateRequest request,
            Authentication authentication
    ) {
        BankScheme scheme = schemeRepository.findById(request.schmId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Scheme not found"
                ));

        if (!"ACTIVE".equals(scheme.getSchmSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme must be ACTIVE"
            );
        }

        if (schemeRateRepository.existsBySchmIdAndEffDt(
                request.schmId(), request.effDt())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme rate already exists for this effective date"
            );
        }

        if ("FIXED".equals(request.rateTyp())) {
            if ((request.minRatePct() == null) != (request.maxRatePct() == null))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide both minimum and maximum rates");
            if (request.minRatePct() != null && (request.minRatePct().signum() < 0
                    || request.maxRatePct().compareTo(new BigDecimal("100")) > 0
                    || request.minRatePct().compareTo(request.maxRatePct()) > 0
                    || request.fixedRatePct() == null
                    || request.fixedRatePct().compareTo(request.minRatePct()) < 0
                    || request.fixedRatePct().compareTo(request.maxRatePct()) > 0))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid fixed rate range");
            if (request.fixedRatePct() == null
                    || request.fixedRatePct().compareTo(BigDecimal.ZERO) < 0
                    || request.fixedRatePct().compareTo(
                    new BigDecimal("100")) > 0
                    || request.baseRateCd() != null
                    || request.marginPct() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid fixed rate configuration"
                );
            }
        } else if ("FLOATING".equals(request.rateTyp())) {
            if (request.baseRateCd() == null
                    || request.baseRateCd().isBlank()
                    || request.marginPct() == null
                    || request.fixedRatePct() != null
                    || request.minRatePct() != null || request.maxRatePct() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid floating rate configuration"
                );
            }

            BankBaseRate base = baseRateRepository
                    .findFirstByRateCdAndEffDtLessThanEqualAndRateStsOrderByEffDtDesc(
                            request.baseRateCd(),
                            request.effDt(),
                            "ACTIVE"
                    )
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "No applicable base rate found"
                    ));
            BigDecimal floatingRate = base.getRatePct().add(request.marginPct());
            if (floatingRate.signum() < 0 || floatingRate.compareTo(new BigDecimal("100")) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Floating effective rate must be between 0% and 100%");
            }
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported rate type"
            );
        }

        BankSchemeRate rate = new BankSchemeRate();
        rate.setSchmId(request.schmId());
        rate.setRateTyp(request.rateTyp());
        rate.setBaseRateCd(request.baseRateCd());
        rate.setFixedRatePct(request.fixedRatePct());
        rate.setMinRatePct(request.minRatePct());
        rate.setMaxRatePct(request.maxRatePct());
        rate.setMarginPct(request.marginPct());
        rate.setEffDt(request.effDt());
        rate.setRateSts("ACTIVE");
        rate.setCrtBy(authentication.getName());

        return schemeRateRepository.saveAndFlush(rate);
    }

    /** Available code list for rate maintenance and floating-rate configuration.
     * An asOf filter returns the latest row effective on or before that date.
     * activeOnly excludes codes whose latest relevant row is inactive.
     */
    @Transactional(readOnly = true)
    public List<BankBaseRateCodeOption> baseRateCodes(LocalDate asOf, boolean activeOnly) {
        List<BankBaseRate> latest = asOf == null
                ? baseRateRepository.findLatestPerCode()
                : baseRateRepository.findLatestPerCodeAsOf(asOf);
        return latest.stream()
                .filter(r -> !activeOnly || "ACTIVE".equals(r.getRateSts()))
                .map(r -> new BankBaseRateCodeOption(r.getRateCd(), r.getRateNm(),
                        r.getCcyCd(), r.getRatePct(), r.getEffDt(), r.getRateSts()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BankBaseRate> baseRateHistory(String rateCd) {
        return baseRateRepository.findByRateCdOrderByEffDtDesc(rateCd);
    }

    @Transactional(readOnly = true)
    public List<BankSchemeRate> schemeRateHistory(Long schmId) {
        return schemeRateRepository.findBySchmIdOrderByEffDtDesc(schmId);
    }

    /** As-of scheme rate policy; for floating rates only the calculated rate is selectable. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> rateOptions(Long schmId, LocalDate date) {
        BankSchemeRate rate = schemeRateRepository
                .findFirstBySchmIdAndEffDtLessThanEqualAndRateStsOrderByEffDtDesc(schmId, date, "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No applicable scheme rate found"));
        BigDecimal value = resolveRate(schmId, date);
        BigDecimal min = "FIXED".equals(rate.getRateTyp()) && rate.getMinRatePct() != null
                ? rate.getMinRatePct() : value;
        BigDecimal max = "FIXED".equals(rate.getRateTyp()) && rate.getMaxRatePct() != null
                ? rate.getMaxRatePct() : value;
        return java.util.Map.of("schmId", schmId, "rateType", rate.getRateTyp(),
                "effectiveDate", date, "minimumRatePct", min, "maximumRatePct", max,
                "defaultRatePct", value);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveRate(Long schmId, LocalDate date) {

        BankSchemeRate schemeRate = schemeRateRepository
                .findFirstBySchmIdAndEffDtLessThanEqualAndRateStsOrderByEffDtDesc(
                        schmId, date, "ACTIVE"
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No applicable scheme rate found"
                ));

        if ("FIXED".equals(schemeRate.getRateTyp())) {
            return schemeRate.getFixedRatePct();
        }

        BankBaseRate baseRate = baseRateRepository
                .findFirstByRateCdAndEffDtLessThanEqualAndRateStsOrderByEffDtDesc(
                        schemeRate.getBaseRateCd(),
                        date,
                        "ACTIVE"
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No applicable base rate found"
                ));

        return baseRate.getRatePct().add(schemeRate.getMarginPct());
    }
}

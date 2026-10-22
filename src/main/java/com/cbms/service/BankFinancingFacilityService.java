package com.cbms.service;

import com.cbms.dto.BankFinancingFacilityCreateRequest;
import com.cbms.entity.BankFinancingFacility;
import com.cbms.entity.BankProduct;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankFinancingFacilityRepository;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class BankFinancingFacilityService {

    private final BankFinancingFacilityRepository facilityRepository;
    private final BankProductRepository productRepository;
    private final BankSchemeRepository schemeRepository;
    private final JdbcTemplate jdbcTemplate;

    public BankFinancingFacilityService(
            BankFinancingFacilityRepository facilityRepository,
            BankProductRepository productRepository,
            BankSchemeRepository schemeRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.facilityRepository = facilityRepository;
        this.productRepository = productRepository;
        this.schemeRepository = schemeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BankFinancingFacility create(
            BankFinancingFacilityCreateRequest request,
            Authentication authentication
    ) {
        Integer customerCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                Integer.class,
                request.custId()
        );

        if (customerCount == null || customerCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer not found"
            );
        }

        BankProduct product = productRepository
                .findById(request.prodId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product not found"
                ));

        BankScheme scheme = schemeRepository
                .findById(request.schmId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Scheme not found"
                ));

        if (!"ACTIVE".equals(product.getProdSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is not ACTIVE"
            );
        }

        if (!"ACTIVE".equals(scheme.getSchmSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme is not ACTIVE"
            );
        }

        if (!request.prodId().equals(scheme.getProdId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Scheme does not belong to product"
            );
        }

        if (!request.facTyp().equals(product.getProdCat())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Facility type does not match product category"
            );
        }

        if (!"MYR".equals(product.getCcyCd())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only MYR financing is currently supported"
            );
        }

        if (scheme.getMinAmt() != null
                && request.apprAmt().compareTo(scheme.getMinAmt()) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Amount is below scheme minimum"
            );
        }

        if (scheme.getMaxAmt() != null
                && request.apprAmt().compareTo(scheme.getMaxAmt()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Amount exceeds scheme maximum"
            );
        }

        if (scheme.getMinTenor() != null
                && request.tenorMth() < scheme.getMinTenor()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tenure is below scheme minimum"
            );
        }

        if (scheme.getMaxTenor() != null
                && request.tenorMth() > scheme.getMaxTenor()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tenure exceeds scheme maximum"
            );
        }

        BankFinancingFacility facility = new BankFinancingFacility();

        facility.setFacRef(
                "FAC-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 20)
                        .toUpperCase()
        );

        facility.setCustId(request.custId());
        facility.setProdId(request.prodId());
        facility.setSchmId(request.schmId());
        facility.setFacTyp(request.facTyp());
        facility.setCcyCd("MYR");
        facility.setApprAmt(request.apprAmt());
        facility.setTenorMth(request.tenorMth());
        facility.setFacSts("DRAFT");
        facility.setRemarks(request.remarks());
        facility.setCrtBy(authentication.getName());
        facility.setUpdBy(authentication.getName());

        return facilityRepository.saveAndFlush(facility);
    }

    @Transactional(readOnly = true)
    public BankFinancingFacility get(Long facId) {
        return facilityRepository.findById(facId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Financing facility not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<BankFinancingFacility> list(
            Long custId,
            String status
    ) {
        if (custId != null) {
            return facilityRepository.findByCustIdOrderByFacIdDesc(custId);
        }

        if (status != null && !status.isBlank()) {
            return facilityRepository.findByFacStsOrderByFacIdDesc(
                    status.toUpperCase()
            );
        }

        return facilityRepository.findAllByOrderByFacIdDesc();
    }
}

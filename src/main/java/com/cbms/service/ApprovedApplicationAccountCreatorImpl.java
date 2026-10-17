package com.cbms.service;

import com.cbms.dto.BankAccountCreateRequest;
import com.cbms.dto.BankAccountResponse;
import com.cbms.entity.BankAccountApplication;
import com.cbms.entity.BankProduct;
import com.cbms.repository.BankProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class ApprovedApplicationAccountCreatorImpl
        implements ApprovedApplicationAccountCreator {

    private final BankAccountService bankAccountService;
    private final BankProductRepository productRepository;

    public ApprovedApplicationAccountCreatorImpl(
            BankAccountService bankAccountService,
            BankProductRepository productRepository
    ) {
        this.bankAccountService = bankAccountService;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long createAccount(
            BankAccountApplication application,
            String username
    ) {
        BankProduct product = productRepository
                .findById(application.getProdId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Product not found"
                ));

        String accountType = product.getProdCat();

        if (accountType == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product category is missing"
            );
        }

        accountType = accountType.trim().toUpperCase(Locale.ROOT);

        if (!"SAVINGS".equals(accountType)
                && !"CURRENT".equals(accountType)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Automatic account opening currently supports "
                            + "SAVINGS and CURRENT products only"
            );
        }

        String currency = product.getCcyCd();

        if (!"MYR".equalsIgnoreCase(currency)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bank account creation currently supports MYR only"
            );
        }

        BankAccountCreateRequest request =
                new BankAccountCreateRequest(
                        accountType,
                        "MYR"
                );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of()
                );

        BankAccountResponse response =
                bankAccountService.create(
                        application.getCustId(),
                        request,
                        authentication
                );

        if (response.acctId() == null) {
            throw new IllegalStateException(
                    "Bank account service returned no account ID"
            );
        }

        return response.acctId();
    }
}

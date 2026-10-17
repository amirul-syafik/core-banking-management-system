package com.cbms.service;

import com.cbms.entity.BankAccountApplication;

public interface ApprovedApplicationAccountCreator {

    Long createAccount(
            BankAccountApplication application,
            String username
    );
}
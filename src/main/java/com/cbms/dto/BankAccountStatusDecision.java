package com.cbms.dto;

import jakarta.validation.constraints.NotNull;

public record BankAccountStatusDecision(@NotNull Boolean approve) {
}

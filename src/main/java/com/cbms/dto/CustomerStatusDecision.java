package com.cbms.dto;

import jakarta.validation.constraints.NotNull;

public record CustomerStatusDecision(@NotNull Boolean approve) {
}

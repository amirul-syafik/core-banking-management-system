package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CustomerStatusUpdateRequest {

    @NotBlank(message = "Customer status is required")
    @Pattern(
            regexp = "PENDING|ACTIVE|INACTIVE|BLOCKED",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Status must be PENDING, ACTIVE, INACTIVE, or BLOCKED"
    )
    private String sts;

    public String getSts() {
        return sts;
    }

    public void setSts(String sts) {
        this.sts = sts;
    }
}

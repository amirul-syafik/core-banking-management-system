package com.cbms.dto;

import com.cbms.entity.BankAccountStatusRequest;

public record BankAccountStatusRequestView(Long requestId, String fromStatus, String toStatus, String requestStatus,
                                           String reason, boolean canReview) {
    public static BankAccountStatusRequestView of(BankAccountStatusRequest r, boolean canReview) {
        return new BankAccountStatusRequestView(r.getRequestId(), r.getFromStatus(), r.getToStatus(), r.getRequestStatus(), r.getReason(), canReview);
    }
}

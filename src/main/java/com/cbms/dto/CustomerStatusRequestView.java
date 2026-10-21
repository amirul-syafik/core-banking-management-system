package com.cbms.dto;
import com.cbms.entity.CustomerStatusRequest;
public record CustomerStatusRequestView(
    Long requestId, String fromStatus, String toStatus, String requestStatus,
    String reason, boolean canReview) {
    public static CustomerStatusRequestView from(CustomerStatusRequest r, boolean canReview) {
        return new CustomerStatusRequestView(r.getRequestId(), r.getFromStatus(), r.getToStatus(),
            r.getRequestStatus(), r.getReason(), canReview);
    }
}

package com.cbms.controller;

import com.cbms.dto.CustomerStatusHistoryRow;
import com.cbms.repository.CustomerStatusRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-status-history")
public class CustomerStatusHistoryController {
    private final CustomerStatusRequestRepository requests;

    public CustomerStatusHistoryController(CustomerStatusRequestRepository requests) {
        this.requests = requests;
    }

    @GetMapping
    public Page<CustomerStatusHistoryRow> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String timestampDirection) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)));
        if (timestampDirection == null || timestampDirection.isBlank()) {
            return requests.findHistory(pageable);
        }
        return switch (timestampDirection.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "ASC" -> requests.findHistoryByEventTimestampAscending(pageable);
            case "DESC" -> requests.findHistoryByEventTimestampDescending(pageable);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "timestampDirection must be ASC or DESC");
        };
    }
}

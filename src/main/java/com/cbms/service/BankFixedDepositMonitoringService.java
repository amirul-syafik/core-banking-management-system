package com.cbms.service;

import com.cbms.repository.BankFixedDepositMonitoringRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class BankFixedDepositMonitoringService {

    private final BankFixedDepositMonitoringRepository repository;

    public BankFixedDepositMonitoringService(
            BankFixedDepositMonitoringRepository repository
    ) {
        this.repository = repository;
    }

    public Map<String, Object> summary() {
        return repository.getSummary();
    }

    public List<Map<String, Object>> upcomingMaturities(int days) {

        if (days < 1 || days > 365) {
            throw new IllegalArgumentException(
                    "Days must be between 1 and 365"
            );
        }

        return repository.getUpcomingMaturities(days);
    }

    public List<Map<String, Object>> overdueMaturities() {
        return repository.getOverdueMaturities();
    }

    public List<Map<String, Object>> missingAccruals() {
        return repository.getMissingAccruals();
    }

    public List<Map<String, Object>> closureHistory() {
        return repository.getClosureHistory();
    }
}

package com.cbms.dto;

import java.util.List;
import java.util.Map;

public record OperationalReportPage(String report, int page, int size,
                                    long totalElements, long totalPages,
                                    List<Map<String, Object>> records) {
}

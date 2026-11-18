package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationLogTimestampSortingTest {
    @TempDir Path temp;

    @Test
    void timestampSortOrdersTheWholeFilteredSetBeforePagination() throws Exception {
        Path file = temp.resolve("cbms.log");
        Files.writeString(file, String.join("\n",
                entry("2026-11-18 09:50:00.100", "first"),
                entry("2026-11-18 09:55:00.010", "second"),
                entry("2026-11-18 10:05:00.900", "third"),
                entry("2026-11-18 10:15:00.000", "fourth"),
                entry("2026-11-18 11:30:00.500", "fifth"),
                entry("2026-11-19 00:05:00.001", "sixth")) + "\n", StandardCharsets.UTF_8);
        ApplicationLogService service = new ApplicationLogService(file.toString());

        Map<String, Object> ascendingFirstPage = service.search("INFO", "TestLogger", "match", "", "",
                0, 2, "timestamp", "ASC");
        Map<String, Object> ascendingSecondPage = service.search("INFO", "TestLogger", "match", "", "",
                1, 2, "timestamp", "ASC");
        Map<String, Object> descendingFirstPage = service.search("INFO", "TestLogger", "match", "", "",
                0, 2, "timestamp", "DESC");

        assertEquals(6, ascendingFirstPage.get("totalElements"));
        assertEquals(List.of("2026-11-18 09:50:00.100", "2026-11-18 09:55:00.010"), timestamps(ascendingFirstPage));
        assertEquals(List.of("2026-11-18 10:05:00.900", "2026-11-18 10:15:00.000"), timestamps(ascendingSecondPage));
        assertEquals(List.of("2026-11-19 00:05:00.001", "2026-11-18 11:30:00.500"), timestamps(descendingFirstPage));
    }

    private String entry(String timestamp, String id) {
        return timestamp + " [worker-1] INFO com.cbms.TestLogger - match=" + id;
    }

    @SuppressWarnings("unchecked")
    private List<String> timestamps(Map<String, Object> response) {
        return ((List<Map<String, String>>) response.get("content"))
                .stream().map(entry -> entry.get("timestamp")).toList();
    }
}

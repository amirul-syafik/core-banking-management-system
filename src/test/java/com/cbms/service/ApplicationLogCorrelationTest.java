package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationLogCorrelationTest {
    @TempDir Path temp;

    @Test
    void applicationLogsCanSearchByRequestId() throws Exception {
        Path file = temp.resolve("cbms.log");
        Files.writeString(file,
                "2026-11-14 10:30:01.123 [http-nio-8080-exec-1] WARN com.cbms.exception.GlobalExceptionHandler - "
                + "[requestId=d3904b7a-05c0-4f6d-8be9-bb7c2dd3451e] API failure method=POST route=/api/accounts status=409\n",
                StandardCharsets.UTF_8);
        var service = new ApplicationLogService(file.toString());
        var result = service.search("WARN", "", "d3904b7a-05c0-4f6d-8be9-bb7c2dd3451e", "", "", 0, 10);
        assertEquals(1, result.get("totalElements"));
        assertTrue(result.toString().contains("d3904b7a-05c0-4f6d-8be9-bb7c2dd3451e"));
    }
}

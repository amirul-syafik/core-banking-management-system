package com.cbms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
public class ApplicationLogService {
    private static final Pattern HEADER = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[([^]]+)] (TRACE|DEBUG|INFO|WARN|ERROR) (.+?) - (.*)$");
    private static final Pattern SECRETS = Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+|(password|passwd|secret|token|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+|Bearer\\s+[A-Za-z0-9._~+/-]+", Pattern.CASE_INSENSITIVE);
    private final Path path;

    public ApplicationLogService(@Value("${app.logs.path:logs/cbms.log}") String configuredPath) {
        this.path = Paths.get(configuredPath).toAbsolutePath().normalize();
    }

    private String redact(String input) {
        Matcher m = SECRETS.matcher(input);
        StringBuffer out = new StringBuffer();
        while (m.find())
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) != null ? m.group(1) + "[REDACTED]" : "[REDACTED]"));
        m.appendTail(out);
        return out.toString();
    }

    // Reads at most the most recent 5 MiB; never accepts a client-supplied filesystem path.
    private List<Map<String, String>> entries() {
        if (!Files.isRegularFile(path)) return List.of();
        try (var channel = java.nio.channels.FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            long start = Math.max(0, size - 5L * 1024 * 1024);
            channel.position(start);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8192);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.write(chunk);
                buffer.clear();
            }
            String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (start > 0) {
                int firstNewline = text.indexOf('\n');
                text = firstNewline < 0 ? "" : text.substring(firstNewline + 1);
            }
            List<Map<String, String>> result = new ArrayList<>();
            Map<String, String> current = null;
            for (String line : text.split("\\R", -1)) {
                Matcher match = HEADER.matcher(line);
                if (match.matches()) {
                    current = new LinkedHashMap<>();
                    current.put("timestamp", match.group(1));
                    current.put("thread", match.group(2));
                    current.put("level", match.group(3));
                    current.put("logger", match.group(4));
                    current.put("message", redact(match.group(5)));
                    result.add(current);
                } else if (current != null && !line.isBlank()) {
                    current.put("message", current.get("message") + "\n" + redact(line));
                }
            }
            return result;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Application logs unavailable");
        }
    }

    public Map<String, Object> search(String level, String logger, String keyword, String from, String to, int page, int size) {
        if (page < 0 || size < 1 || size > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0; size must be 1..200");
        List<Map<String, String>> matches = filter(level, logger, keyword, from, to);
        long offset = (long) page * size;
        List<Map<String, String>> content = offset >= matches.size() ? List.of() : matches.subList((int) offset, (int) Math.min(matches.size(), offset + size));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", matches.size());
        response.put("content", content);
        response.put("source", "current rolling log file; last 5 MiB maximum");
        return response;
    }

    private List<Map<String, String>> filter(String level, String logger, String keyword, String from, String to) {
        String lev = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
        if (!lev.isEmpty() && !List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(lev))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid level");
        if (from != null && !from.isBlank()) validateDate(from);
        if (to != null && !to.isBlank()) validateDate(to);
        String l = logger == null ? "" : logger.toLowerCase(Locale.ROOT);
        String k = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        return entries().stream().filter(e -> lev.isEmpty() || e.get("level").equals(lev))
                .filter(e -> e.get("logger").toLowerCase(Locale.ROOT).contains(l))
                .filter(e -> e.get("message").toLowerCase(Locale.ROOT).contains(k))
                .filter(e -> from == null || from.isBlank() || e.get("timestamp").compareTo(from.replace('T', ' ')) >= 0)
                .filter(e -> to == null || to.isBlank() || e.get("timestamp").compareTo(to.replace('T', ' ')) <= 0)
                .collect(Collectors.toList());
    }

    private void validateDate(String date) {
        try {
            LocalDateTime.parse(date.replace(' ', 'T').substring(0, 19));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use yyyy-MM-ddTHH:mm:ss");
        }
    }

    public Map<String, Object> summary() {
        var all = entries();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        for (String level : List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR"))
            result.put(level.toLowerCase(Locale.ROOT), all.stream().filter(e -> level.equals(e.get("level"))).count());
        result.put("source", "current rolling log file; last 5 MiB maximum");
        return result;
    }

    public String download(String level, String logger, String keyword, String from, String to) {
        List<Map<String, String>> matches = filter(level, logger, keyword, from, to);
        if (matches.size() > 10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Narrow filters: export limited to 10000 entries");
        return matches.stream().map(e -> e.get("timestamp") + " [" + e.get("thread") + "] " + e.get("level") + " " + e.get("logger") + " - " + e.get("message")).collect(Collectors.joining("\n"));
    }
}

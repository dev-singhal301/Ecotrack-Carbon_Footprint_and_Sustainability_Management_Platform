package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * Reports & Export module. A single unified "Sustainability Performance
 * Report" replaces the four previous report types (carbon footprint / goals
 * / challenges / all-time summary) — one report, fixed to the past 30 days,
 * covering carbon footprint, XP, goals, and challenges together, in three
 * downloadable formats.
 *
 * No @CrossOrigin(origins = "*") here on purpose — SecurityConfig's global
 * CORS bean already covers this, and it's the pattern the most recently
 * added controller (UserProfileController) follows too, rather than the
 * older, redundant per-controller annotation.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv");

    private final ReportService reportService;
    private final UserRepository userRepository;

    public ReportController(ReportService reportService, UserRepository userRepository) {
        this.reportService = reportService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/reports?format=pdf
     * GET /api/reports?format=xlsx
     * GET /api/reports?format=csv
     * Always covers the past 30 days (today inclusive) — carbon footprint,
     * XP, goals, and challenges in one report. Defaults to PDF if format is
     * omitted.
     */
    @GetMapping
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam(name = "format", defaultValue = "pdf") String format,
            Authentication authentication) {

        String normalizedFormat = normalizeFormat(format);
        User user = getCurrentUser(authentication);

        byte[] reportBytes = reportService.generateReport(user.getId(), normalizedFormat);

        String filename = "sustainability-report-" + LocalDate.now() + "." + normalizedFormat;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaTypeFor(normalizedFormat))
                .body(reportBytes);
    }

    private String normalizeFormat(String format) {
        String normalized = format.toLowerCase(Locale.ROOT);
        if (!normalized.equals("pdf") && !normalized.equals("xlsx") && !normalized.equals("csv")) {
            throw new IllegalArgumentException("format must be 'pdf', 'xlsx', or 'csv'");
        }
        return normalized;
    }

    private MediaType mediaTypeFor(String normalizedFormat) {
        return switch (normalizedFormat) {
            case "xlsx" -> XLSX_MEDIA_TYPE;
            case "csv" -> CSV_MEDIA_TYPE;
            default -> MediaType.APPLICATION_PDF;
        };
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    // ── Exception Handlers ──

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleServerError(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", ex.getMessage()));
    }
}
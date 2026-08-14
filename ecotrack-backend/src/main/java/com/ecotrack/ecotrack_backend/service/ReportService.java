package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.dto.GoalResponse;
import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color BRAND_GREEN = new Color(0x1F, 0x7A, 0x4D);
    private static final Color LIGHT_GREEN = new Color(0xE8, 0xF5, 0xEC);
    private static final Color OUTCOME_ACHIEVED = BRAND_GREEN;
    private static final Color OUTCOME_NOT_ACHIEVED = new Color(0xB0, 0x2A, 0x2A);
    private static final Color OUTCOME_IN_PROGRESS = new Color(0x2A, 0x5C, 0xA0);

    private final UserRepository userRepository;
    private final CarbonEntryRepository carbonEntryRepository;
    private final GoalService goalService;
    private final UserChallengeService userChallengeService;

    public ReportService(UserRepository userRepository, CarbonEntryRepository carbonEntryRepository,
            GoalService goalService, UserChallengeService userChallengeService) {
        this.userRepository = userRepository;
        this.carbonEntryRepository = carbonEntryRepository;
        this.goalService = goalService;
        this.userChallengeService = userChallengeService;
    }

    @Transactional(readOnly = true)
    public byte[] generateReport(Long userId, String format) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        List<CarbonEntry> entries = carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);

        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(periodDays - 1);
        List<CarbonEntry> previousEntries = prevTo.isBefore(prevFrom)
                ? List.of()
                : carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, prevFrom, prevTo);

        List<GoalResponse> goals = goalService.getGoalsForUser(userId).stream()
                .filter(g -> !g.getStartDate().isAfter(to) && !g.getEndDate().isBefore(from))
                .toList();
        List<GoalReportRow> goalRows = goals.stream().map(g -> toReportRow(userId, g)).toList();
        GoalReportData goalData = buildGoalReportData(user, goalRows);

        LocalDateTime periodStart = from.atStartOfDay();
        LocalDateTime periodEnd = to.atTime(23, 59, 59);
        List<UserChallengeResponse> joined = userChallengeService.getUserChallenges(user.getEmail()).stream()
                .filter(c -> !"NOT_JOINED".equals(c.getUserStatus()))
                .filter(c -> c.getJoinedAt() != null && !c.getJoinedAt().isAfter(periodEnd))
                .filter(c -> c.getCompletedAt() == null || !c.getCompletedAt().isBefore(periodStart))
                .toList();
        ChallengeReportData challengeData = buildChallengeReportData(user, joined);

        CarbonFootprintReportData data = buildReportData(user, from, to, entries, previousEntries, goalData,
                challengeData);

        if ("csv".equalsIgnoreCase(format)) {
            return buildCarbonFootprintCsv(data);
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return buildCarbonFootprintExcel(data);
        }
        return buildCarbonFootprintPdf(data);
    }

    private CarbonFootprintReportData buildReportData(User user, LocalDate from, LocalDate to,
            List<CarbonEntry> entries, List<CarbonEntry> previousEntries, GoalReportData goalData,
            ChallengeReportData challengeData) {
        BigDecimal total = entries.stream()
                .map(CarbonEntry::getCarbonEmission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO, CarbonEntry::getCarbonEmission, BigDecimal::add)));

        List<CategoryRow> categoryRows = byCategory.entrySet().stream()
                .map(e -> {
                    double pct = total.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue().multiply(BigDecimal.valueOf(100))
                                    .divide(total, 1, RoundingMode.HALF_UP).doubleValue()
                            : 0.0;
                    return new CategoryRow(e.getKey(), e.getValue(), pct);
                })
                .sorted((a, b) -> b.emissions.compareTo(a.emissions))
                .toList();

        BigDecimal previousTotal = previousEntries.stream()
                .map(CarbonEntry::getCarbonEmission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal percentChange = null;
        if (previousTotal.compareTo(BigDecimal.ZERO) > 0) {
            percentChange = total.subtract(previousTotal)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(previousTotal, 1, RoundingMode.HALF_UP);
        }

        List<TrendPoint> trendPoints = aggregateTrend(entries, from, to);

        int totalXp = goalData.totalXpEarned() + challengeData.totalXpEarned();

        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        List<String> insights = buildInsights(total, categoryRows, percentChange, goalData, challengeData, entries,
                periodDays);
        List<String> recommendations = buildRecommendations(categoryRows, goalData, challengeData, percentChange,
                entries, periodDays);

        return new CarbonFootprintReportData(user, from, to, total, entries.size(), categoryRows,
                previousTotal, percentChange, trendPoints, goalData, challengeData, totalXp, insights,
                recommendations, entries);
    }

    private List<TrendPoint> aggregateTrend(List<CarbonEntry> entries, LocalDate from, LocalDate to) {
        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;

        if (periodDays <= 31) {
            Map<LocalDate, BigDecimal> byDay = entries.stream()
                    .collect(Collectors.groupingBy(CarbonEntry::getActivityDate,
                            Collectors.reducing(BigDecimal.ZERO, CarbonEntry::getCarbonEmission, BigDecimal::add)));
            DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("d MMM");
            return from.datesUntil(to.plusDays(1))
                    .map(d -> new TrendPoint(d.format(dayFmt), byDay.getOrDefault(d, BigDecimal.ZERO)))
                    .toList();
        }

        if (periodDays <= 120) {
            Map<LocalDate, BigDecimal> weekStartTotals = new java.util.TreeMap<>();
            LocalDate cursor = from;
            while (!cursor.isAfter(to)) {
                weekStartTotals.putIfAbsent(cursor, BigDecimal.ZERO);
                cursor = cursor.plusDays(7);
            }
            for (CarbonEntry entry : entries) {
                long offset = java.time.temporal.ChronoUnit.DAYS.between(from, entry.getActivityDate()) / 7;
                LocalDate weekStart = from.plusDays(offset * 7);
                weekStartTotals.merge(weekStart, entry.getCarbonEmission(), BigDecimal::add);
            }
            DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("d MMM");
            return weekStartTotals.entrySet().stream()
                    .map(e -> new TrendPoint("Wk of " + e.getKey().format(weekFmt), e.getValue()))
                    .toList();
        }

        Map<String, BigDecimal> monthTotals = new java.util.TreeMap<>();
        java.time.YearMonth cursorMonth = java.time.YearMonth.from(from);
        java.time.YearMonth endMonth = java.time.YearMonth.from(to);
        while (!cursorMonth.isAfter(endMonth)) {
            monthTotals.putIfAbsent(cursorMonth.toString(), BigDecimal.ZERO);
            cursorMonth = cursorMonth.plusMonths(1);
        }
        for (CarbonEntry entry : entries) {
            String key = java.time.YearMonth.from(entry.getActivityDate()).toString();
            monthTotals.merge(key, entry.getCarbonEmission(), BigDecimal::add);
        }
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");
        return monthTotals.entrySet().stream()
                .map(e -> new TrendPoint(java.time.YearMonth.parse(e.getKey()).format(monthFmt), e.getValue()))
                .toList();
    }

    private List<String> buildInsights(BigDecimal total, List<CategoryRow> categoryRows, BigDecimal percentChange,
            GoalReportData goalData, ChallengeReportData challengeData, List<CarbonEntry> entries, long periodDays) {
        List<String> insights = new java.util.ArrayList<>();

        if (!categoryRows.isEmpty()) {
            CategoryRow top = categoryRows.get(0);
            insights.add(prettifyCategory(top.category()) + " is the largest emission source, contributing "
                    + top.percentageShare() + "% of total emissions (" + formatKg(top.emissions()) + ").");
        } else {
            insights.add("No carbon activity was logged during this reporting period.");
        }

        // Tracking consistency: how many distinct days had at least one
        // logged activity, out of the period length. Two users with the
        // same total emissions can look identical elsewhere in the report
        // (one logging steadily, one in a few large bursts) — this is the
        // only place that distinction shows up.
        if (!entries.isEmpty() && periodDays > 0) {
            long distinctDays = entries.stream().map(CarbonEntry::getActivityDate).distinct().count();
            if (distinctDays < periodDays) {
                insights.add("Activity was logged on " + distinctDays + " of the " + periodDays
                        + " days in this period.");
            } else {
                insights.add("Activity was logged every day this period (" + periodDays + " of " + periodDays
                        + " days).");
            }
        }

        // Daily average, narrated (it already appears as its own KPI card,
        // but never as a sentence elsewhere in the report).
        if (total.compareTo(BigDecimal.ZERO) > 0 && periodDays > 0) {
            BigDecimal dailyAverage = total.divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
            insights.add("Daily average was " + formatKg(dailyAverage) + "/day across the period.");
        }

        if (percentChange != null) {
            if (percentChange.compareTo(BigDecimal.ZERO) < 0) {
                insights.add("Emissions decreased by " + percentChange.abs()
                        + "% compared with the previous period — a positive trend.");
            } else if (percentChange.compareTo(BigDecimal.ZERO) > 0) {
                insights.add("Emissions increased by " + percentChange
                        + "% compared with the previous period, driven largely by "
                        + (categoryRows.isEmpty() ? "logged activity" : prettifyCategory(categoryRows.get(0).category()))
                        + ".");
            } else {
                insights.add("Emissions were essentially unchanged compared with the previous period.");
            }
        }

        if (categoryRows.size() >= 2) {
            CategoryRow top = categoryRows.get(0);
            CategoryRow second = categoryRows.get(1);
            insights.add(prettifyCategory(top.category()) + " and " + prettifyCategory(second.category())
                    + " together account for " + roundPct(top.percentageShare() + second.percentageShare())
                    + "% of all emissions this period.");
        }

        if (goalData.totalGoals() > 0) {
            insights.add(goalData.achievedCount() + " of " + goalData.totalGoals()
                    + " goals have been achieved (" + goalData.successRate() + "% success rate).");
        }

        // At-risk goals: in-progress goals that have already burned through
        // most of their emissions budget. This is real signal that already
        // exists in GoalReportRow.budgetUsedPercent() but was previously
        // never surfaced anywhere in the report.
        long atRiskGoals = goalData.rows().stream()
                .filter(r -> "In Progress".equals(r.outcome()) && r.budgetUsedPercent() != null
                        && r.budgetUsedPercent().compareTo(BigDecimal.valueOf(80)) > 0)
                .count();
        if (atRiskGoals > 0) {
            insights.add(atRiskGoals + " goal(s) have used over 80% of their emissions budget and are at risk of "
                    + "being missed.");
        }

        if (challengeData.totalJoined() > 0) {
            insights.add("User has completed " + challengeData.completedCount() + " of "
                    + challengeData.totalJoined() + " joined challenges, earning "
                    + challengeData.totalXpEarned() + " XP from challenge activity.");
        }

        long unresolvedChallenges = challengeData.expiredCount() + challengeData.cancelledCount();
        if (unresolvedChallenges > 0) {
            insights.add(unresolvedChallenges + " challenge(s) expired or were cancelled without completion this "
                    + "period.");
        }

        return insights.stream().limit(9).toList();
    }

    private double roundPct(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private List<String> buildRecommendations(List<CategoryRow> categoryRows, GoalReportData goalData,
            ChallengeReportData challengeData, BigDecimal percentChange, List<CarbonEntry> entries,
            long periodDays) {
        List<String> recommendations = new java.util.ArrayList<>();

        if (!categoryRows.isEmpty()) {
            String topCategory = categoryRows.get(0).category();
            recommendations.add(switch (topCategory) {
                case "ELECTRICITY" -> "Electricity is the leading emission source — prioritize energy-efficiency "
                        + "measures such as LED lighting, efficient appliances, and shifting usage away from "
                        + "peak-demand hours.";
                case "TRANSPORTATION" -> "Transportation dominates the footprint — consider lower-emission "
                        + "alternatives such as public transit, carpooling, cycling, or trip consolidation.";
                case "FOOD" -> "Food-related emissions are the largest contributor — reducing red meat and dairy "
                        + "consumption in favor of plant-based options can meaningfully lower this category.";
                case "WASTE" -> "Waste is the leading emission source — increasing recycling/composting rates and "
                        + "reducing single-use items would have the most impact here.";
                case "WATER" -> "Water usage leads emissions this period — shorter showers, efficient fixtures, "
                        + "and fixing leaks are the highest-leverage actions.";
                case "FUEL" -> "Fuel consumption is the top contributor — reducing idle time, combining errands, "
                        + "and maintaining vehicles for efficiency will help most.";
                default -> "Focus reduction efforts on " + prettifyCategory(topCategory)
                        + ", the largest contributor to this period's footprint.";
            });
        }

        // Logging consistency: fewer than half the period's days had any
        // logged activity at all.
        if (!entries.isEmpty() && periodDays > 0) {
            long distinctDays = entries.stream().map(CarbonEntry::getActivityDate).distinct().count();
            if (distinctDays < periodDays / 2.0) {
                recommendations.add("Emissions were only logged on " + distinctDays + " of " + periodDays
                        + " days — more consistent logging gives a truer picture and better trend data.");
            }
        }

        // Rising trend flag: a sharp period-over-period increase, separate
        // from the plain "increased by X%" line already shown next to the
        // trend chart — this one is a call to action, not just a fact.
        if (percentChange != null && percentChange.compareTo(BigDecimal.valueOf(20)) > 0) {
            recommendations.add("Emissions rose sharply (" + percentChange + "%) compared with the previous "
                    + "period — worth checking what changed before it becomes a pattern.");
        }

        if (goalData.totalGoals() > 0 && goalData.successRate().compareTo(BigDecimal.valueOf(50)) < 0) {
            recommendations.add("Goal achievement is below 50% — focus on the active in-progress goals before "
                    + "starting new ones to build momentum.");
        } else if (goalData.totalGoals() == 0) {
            recommendations.add("No goals have been set yet — creating a reduction goal is a strong next step to "
                    + "turn tracking into measurable progress.");
        }

        if (challengeData.totalJoined() == 0) {
            recommendations.add("No community challenges joined yet — joining one is a low-effort way to build "
                    + "consistent sustainable habits while earning XP.");
        } else if (challengeData.expiredCount() > 0) {
            recommendations.add(challengeData.expiredCount() + " challenge(s) expired without completion — "
                    + "consider joining fewer at a time to actually finish them.");
        }

        return recommendations.stream().limit(5).toList();
    }

    private byte[] buildCarbonFootprintPdf(CarbonFootprintReportData data) {
        try {
            Document document = new Document(PageSize.A4, 40, 40, 55, 46);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageNumberFooter());
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_GREEN);
            Font brandFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.DARK_GRAY);
            Font subtitleFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);
            Font sectionFont = new Font(Font.HELVETICA, 13, Font.BOLD, Color.DARK_GRAY);
            Font subsectionFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.DARK_GRAY);
            Font bodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font smallBodyFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
            Font kpiValueFont = new Font(Font.HELVETICA, 17, Font.BOLD, BRAND_GREEN);
            Font kpiLabelFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
            Font tableHeaderFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);

            long periodDays = java.time.temporal.ChronoUnit.DAYS.between(data.from(), data.to()) + 1;
            BigDecimal dailyAverage = periodDays > 0
                    ? data.totalEmission().divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            String topCategory = data.categoryRows().isEmpty()
                    ? "N/A"
                    : prettifyCategory(data.categoryRows().get(0).category());

            document.add(new Paragraph("EcoTrack", brandFont));
            document.add(new Paragraph("Sustainability Performance Report", titleFont));

            Paragraph subtitle = new Paragraph(
                    "User: " + sanitizeForPdf(data.user().getName()) + "  |  " + data.user().getEmail() + "\n" +
                            "Reporting period: " + data.from().format(DATE_FMT) + " to " + data.to().format(DATE_FMT)
                            + "\n" +
                            "Generated: " + LocalDate.now().format(DATE_FMT),
                    subtitleFont);
            subtitle.setSpacingBefore(6);
            subtitle.setSpacingAfter(16);
            document.add(subtitle);

            addDivider(document);

            document.add(new Paragraph("Executive Summary", sectionFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(buildExecutiveSummary(data, topCategory), bodyFont));
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("KPI Snapshot", sectionFont));
            document.add(Chunk.NEWLINE);

            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(22);
            kpiTable.addCell(kpiCell("Total Carbon Emissions", formatKg(data.totalEmission()), kpiValueFont,
                    kpiLabelFont));
            kpiTable.addCell(kpiCell("Daily Average", formatKg(dailyAverage) + "/day", kpiValueFont, kpiLabelFont));
            kpiTable.addCell(kpiCell("Logged Activities", String.valueOf(data.entryCount()), kpiValueFont,
                    kpiLabelFont));
            kpiTable.addCell(kpiCell("XP Earned", String.valueOf(data.totalXpEarned()), kpiValueFont, kpiLabelFont));
            kpiTable.addCell(kpiCell("Goals Achieved",
                    data.goalData().achievedCount() + " / " + data.goalData().totalGoals(), kpiValueFont,
                    kpiLabelFont));
            kpiTable.addCell(kpiCell("Goal Success Rate", data.goalData().successRate() + "%", kpiValueFont,
                    kpiLabelFont));
            kpiTable.addCell(kpiCell("Challenges Completed",
                    data.challengeData().completedCount() + " / " + data.challengeData().totalJoined(), kpiValueFont,
                    kpiLabelFont));
            kpiTable.addCell(kpiCell("Challenge Completion Rate", completionPct(data.challengeData()) + "%",
                    kpiValueFont, kpiLabelFont));
            document.add(kpiTable);

            document.newPage();

            document.add(new Paragraph("Carbon Footprint Performance", sectionFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Carbon Emissions Trend", subsectionFont));
            document.add(Chunk.NEWLINE);
            if (data.trendPoints().size() < 2 || data.totalEmission().compareTo(BigDecimal.ZERO) == 0) {
                document.add(new Paragraph("Not enough activity logged this period to plot a trend.", bodyFont));
            } else {
                addVectorChart(writer, document, buildTrendLineChart(data.trendPoints()), 500, 230);
                document.add(new Paragraph(buildTrendSentence(data), bodyFont));
                document.add(Chunk.NEWLINE);
            }

            document.add(new Paragraph("Current vs Previous Period", subsectionFont));
            document.add(Chunk.NEWLINE);

            PdfPTable compareTable = new PdfPTable(3);
            compareTable.setWidthPercentage(100);
            compareTable.setSpacingAfter(8);
            compareTable.addCell(kpiCell("Current Period", formatKg(data.totalEmission()), kpiValueFont,
                    kpiLabelFont));
            compareTable.addCell(kpiCell("Previous Period", formatKg(data.previousTotalEmission()), kpiValueFont,
                    kpiLabelFont));
            String changeLabel = data.percentChange() == null ? "N/A" : (data.percentChange().signum() > 0 ? "+" : "")
                    + data.percentChange() + "%";
            compareTable.addCell(kpiCell("Change", changeLabel,
                    changeFont(data.percentChange(), kpiValueFont), kpiLabelFont));
            document.add(compareTable);

            document.add(new Paragraph(buildChangeSentence(data.percentChange()), bodyFont));

            document.newPage();

            document.add(new Paragraph("Emissions by Category", sectionFont));
            document.add(Chunk.NEWLINE);

            if (data.categoryRows().isEmpty()) {
                document.add(new Paragraph("No activity logged for this period.", bodyFont));
            } else {
                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100);
                table.setWidths(new float[] { 2f, 1.3f, 1f });
                table.setSpacingAfter(16);
                for (String header : new String[] { "Category", "Emissions", "Share" }) {
                    PdfPCell cell = new PdfPCell(new Phrase(header, tableHeaderFont));
                    cell.setBackgroundColor(BRAND_GREEN);
                    cell.setPadding(6);
                    table.addCell(cell);
                }
                boolean stripe = false;
                for (CategoryRow row : data.categoryRows()) {
                    Color bg = stripe ? LIGHT_GREEN : Color.WHITE;
                    table.addCell(dataCell(prettifyCategory(row.category()), smallBodyFont, bg));
                    table.addCell(dataCell(formatKg(row.emissions()), smallBodyFont, bg));
                    table.addCell(dataCell(row.percentageShare() + "%", smallBodyFont, bg));
                    stripe = !stripe;
                }
                document.add(table);

                addVectorChart(writer, document, buildCategoryHorizontalBarChart(data.categoryRows()), 500,
                        60 + data.categoryRows().size() * 34);
                document.add(new Paragraph(buildCategorySentence(data.categoryRows()), bodyFont));
            }

            document.newPage();

            document.add(new Paragraph("Sustainability Engagement", sectionFont));
            document.add(new Paragraph("Goals and challenges active at any point during the reporting period.",
                    subtitleFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Goal Performance", subsectionFont));
            document.add(Chunk.NEWLINE);
            if (data.goalData().totalGoals() == 0) {
                document.add(new Paragraph("No goals created yet.", bodyFont));
            } else {
                addVectorChart(writer, document, buildOutcomeProgressChart(
                        "Goals", data.goalData().achievedCount(), data.goalData().inProgressCount(),
                        data.goalData().notAchievedCount()), 500, 90);

                Paragraph goalStats = new Paragraph(
                        "Total: " + data.goalData().totalGoals() + "   |   Achieved: "
                                + data.goalData().achievedCount() + "   |   In Progress: "
                                + data.goalData().inProgressCount() + "   |   Not Achieved: "
                                + data.goalData().notAchievedCount() + "   |   Achievement Rate: "
                                + data.goalData().successRate() + "%",
                        smallBodyFont);
                goalStats.setSpacingAfter(6);
                document.add(goalStats);
                Paragraph goalSentence = new Paragraph(buildGoalSentence(data.goalData()), bodyFont);
                goalSentence.setSpacingAfter(16);
                document.add(goalSentence);
            }

            document.add(new Paragraph("Challenge Performance", subsectionFont));
            document.add(Chunk.NEWLINE);
            if (data.challengeData().totalJoined() == 0) {
                document.add(new Paragraph("No challenges joined yet.", bodyFont));
            } else {
                long activeCount = data.challengeData().inProgressCount();
                addVectorChart(writer, document, buildOutcomeProgressChart(
                        "Challenges", data.challengeData().completedCount(), activeCount,
                        data.challengeData().expiredCount() + data.challengeData().cancelledCount()), 500, 90);

                Paragraph challengeStats = new Paragraph(
                        "Total Joined: " + data.challengeData().totalJoined() + "   |   Completed: "
                                + data.challengeData().completedCount() + "   |   Active: " + activeCount
                                + "   |   Completion Rate: " + completionPct(data.challengeData()) + "%"
                                + "   |   XP Earned: " + data.challengeData().totalXpEarned(),
                        smallBodyFont);
                challengeStats.setSpacingAfter(6);
                document.add(challengeStats);
                document.add(new Paragraph(buildChallengeSentence(data.challengeData()), bodyFont));
            }

            document.newPage();

            document.add(new Paragraph("Key Insights", sectionFont));
            document.add(Chunk.NEWLINE);
            document.add(bulletList(data.insights(), bodyFont));
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Recommended Actions", sectionFont));
            document.add(Chunk.NEWLINE);
            document.add(bulletList(data.recommendations(), bodyFont));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate PDF report: " + e.getMessage(), e);
        }
    }

    private String completionPct(ChallengeReportData c) {
        if (c.totalJoined() == 0)
            return "0";
        return BigDecimal.valueOf(c.completedCount()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(c.totalJoined()), 1, RoundingMode.HALF_UP).toPlainString();
    }

    private Font changeFont(BigDecimal percentChange, Font base) {
        if (percentChange == null)
            return base;
        Color color = percentChange.compareTo(BigDecimal.ZERO) < 0 ? OUTCOME_ACHIEVED
                : (percentChange.compareTo(BigDecimal.ZERO) > 0 ? OUTCOME_NOT_ACHIEVED : Color.DARK_GRAY);
        return new Font(base.getFamily(), base.getSize(), Font.BOLD, color);
    }

    private String buildTrendSentence(CarbonFootprintReportData data) {
        List<TrendPoint> points = data.trendPoints();
        if (points.isEmpty()) {
            return "No activity was logged during this period.";
        }

        TrendPoint peak = points.get(0);
        for (TrendPoint p : points) {
            if (p.emission().compareTo(peak.emission()) > 0) {
                peak = p;
            }
        }

        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(data.from(), data.to()) + 1;
        BigDecimal dailyAverage = periodDays > 0
                ? data.totalEmission().divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        if (peak.emission().compareTo(BigDecimal.ZERO) == 0) {
            return "Daily average across the period was " + formatKg(dailyAverage) + ".";
        }

        return "The highest emissions were recorded around " + peak.label() + ", at " + formatKg(peak.emission())
                + ". Daily average across the period was " + formatKg(dailyAverage) + ".";
    }

    private String buildCategorySentence(List<CategoryRow> categoryRows) {
        if (categoryRows.isEmpty()) {
            return "No activity was logged during this period.";
        }

        CategoryRow top = categoryRows.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(prettifyCategory(top.category())).append(" is the largest contributor, at ")
                .append(top.percentageShare()).append("% of total emissions (").append(formatKg(top.emissions()))
                .append(")");

        if (categoryRows.size() >= 2) {
            CategoryRow second = categoryRows.get(1);
            sb.append(", followed by ").append(prettifyCategory(second.category())).append(" at ")
                    .append(second.percentageShare()).append("%");
        }
        sb.append(". Bars are sorted from the highest to the lowest emitter, top to bottom.");

        return sb.toString();
    }

    private String buildGoalSentence(GoalReportData goalData) {
        StringBuilder sb = new StringBuilder();
        sb.append(goalData.achievedCount()).append(" of ").append(goalData.totalGoals())
                .append(" goals active this period have been achieved, a ").append(goalData.successRate())
                .append("% success rate");

        if (goalData.inProgressCount() > 0 && goalData.notAchievedCount() > 0) {
            sb.append(", with ").append(goalData.inProgressCount()).append(" still in progress and ")
                    .append(goalData.notAchievedCount()).append(" not achieved.");
        } else if (goalData.inProgressCount() > 0) {
            sb.append(", with ").append(goalData.inProgressCount()).append(" still in progress.");
        } else if (goalData.notAchievedCount() > 0) {
            sb.append(", with ").append(goalData.notAchievedCount()).append(" not achieved.");
        } else {
            sb.append(".");
        }

        return sb.toString();
    }

    private String buildChallengeSentence(ChallengeReportData challengeData) {
        long notCompleted = challengeData.expiredCount() + challengeData.cancelledCount();
        StringBuilder sb = new StringBuilder();
        sb.append(challengeData.completedCount()).append(" of ").append(challengeData.totalJoined())
                .append(" joined challenges have been completed, earning ").append(challengeData.totalXpEarned())
                .append(" XP");

        if (challengeData.inProgressCount() > 0 && notCompleted > 0) {
            sb.append(", with ").append(challengeData.inProgressCount()).append(" still active and ")
                    .append(notCompleted).append(" expired or cancelled.");
        } else if (challengeData.inProgressCount() > 0) {
            sb.append(", with ").append(challengeData.inProgressCount()).append(" still active.");
        } else if (notCompleted > 0) {
            sb.append(", with ").append(notCompleted).append(" expired or cancelled.");
        } else {
            sb.append(".");
        }

        return sb.toString();
    }

    private String buildChangeSentence(BigDecimal percentChange) {
        if (percentChange == null) {
            return "No comparable data was logged in the previous period.";
        }
        if (percentChange.compareTo(BigDecimal.ZERO) < 0) {
            return "Emissions decreased by " + percentChange.abs() + "% compared with the previous period.";
        }
        if (percentChange.compareTo(BigDecimal.ZERO) > 0) {
            return "Emissions increased by " + percentChange + "% compared with the previous period.";
        }
        return "Emissions showed no significant change compared with the previous period.";
    }

    private String buildExecutiveSummary(CarbonFootprintReportData data, String topCategory) {
        StringBuilder sb = new StringBuilder();
        sb.append("Over the reporting period, ").append(sanitizeForPdf(data.user().getName()))
                .append(" logged ").append(formatKg(data.totalEmission()))
                .append(" across ").append(data.entryCount()).append(" activities. ");

        if (!data.categoryRows().isEmpty()) {
            sb.append(topCategory).append(" was the largest emission source, contributing ")
                    .append(data.categoryRows().get(0).percentageShare()).append("% of the total. ");
        }

        if (data.percentChange() != null) {
            if (data.percentChange().compareTo(BigDecimal.ZERO) < 0) {
                sb.append("Emissions fell ").append(data.percentChange().abs())
                        .append("% versus the previous period. ");
            } else if (data.percentChange().compareTo(BigDecimal.ZERO) > 0) {
                sb.append("Emissions rose ").append(data.percentChange()).append("% versus the previous period. ");
            } else {
                sb.append("Emissions were flat versus the previous period. ");
            }
        }

        if (data.goalData().totalGoals() > 0) {
            sb.append(data.goalData().achievedCount()).append(" of ").append(data.goalData().totalGoals())
                    .append(" goals have been achieved (").append(data.goalData().successRate())
                    .append("% success rate). ");
        } else {
            sb.append("No sustainability goals have been created yet. ");
        }

        if (data.challengeData().totalJoined() > 0) {
            sb.append("The user has joined ").append(data.challengeData().totalJoined())
                    .append(" community challenges and completed ").append(data.challengeData().completedCount())
                    .append(", earning ").append(data.totalXpEarned()).append(" total XP across goals and challenges.");
        } else {
            sb.append("No community challenges have been joined yet.");
        }

        return sb.toString();
    }

    private String buildMethodologyText(CarbonFootprintReportData data) {
        return "Reporting period: " + data.from().format(DATE_FMT) + " to " + data.to().format(DATE_FMT) + ". "
                + "Data source: activities logged directly by the user in EcoTrack, each converted to kg CO2e "
                + "using the emission factor associated with its category at the time of entry. "
                + "Category totals sum each activity's carbon_emission value per CarbonCategory, sorted from "
                + "highest to lowest contribution. Trend points aggregate emissions into daily, weekly, or "
                + "monthly buckets depending on period length, so short ranges stay granular and long ranges "
                + "stay readable. The previous-period comparison uses an immediately preceding window of the "
                + "same length as the reporting period. Goals are included if their active window (start date "
                + "to end date) overlaps the reporting period; challenges are included if they were joined "
                + "on/before the period end and were still active or completed/ended on/after the period start. "
                + "Goal achievement reflects each included goal's current status "
                + "(Achieved/In Progress/Not Achieved); challenge completion reflects each included challenge's "
                + "current status, not the date it was completed. XP earned totals XP banked from completed "
                + "goals and completed challenges within this scoped set only.";
    }

    private PdfPTable bulletList(List<String> items, Font font) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        if (items.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("Nothing to report yet.", font));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(3);
            table.addCell(cell);
            return table;
        }
        for (String item : items) {
            PdfPCell cell = new PdfPCell(new Phrase("\u2022  " + sanitizeForPdf(item), font));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPaddingBottom(6);
            table.addCell(cell);
        }
        return table;
    }

    private class PageNumberFooter extends com.lowagie.text.pdf.PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfPTable footer = new PdfPTable(1);
            footer.setTotalWidth(document.right() - document.left());
            Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
            PdfPCell cell = new PdfPCell(new Phrase("EcoTrack Sustainability Report  |  Page "
                    + writer.getPageNumber(), footerFont));
            cell.setBorder(Rectangle.TOP);
            cell.setBorderColor(new Color(0xE0, 0xE0, 0xE0));
            cell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            cell.setPaddingTop(6);
            footer.addCell(cell);
            footer.writeSelectedRows(0, -1, document.left(), document.bottom() - 10, writer.getDirectContent());
        }
    }

    private JFreeChart buildTrendLineChart(List<TrendPoint> points) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (TrendPoint p : points) {
            dataset.addValue(p.emission(), "kg CO2e", p.label());
        }

        JFreeChart chart = ChartFactory.createLineChart(null, "", "kg CO2e", dataset, PlotOrientation.VERTICAL,
                false, false, false);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setBorderVisible(false);
        chart.setBackgroundPaint(Color.WHITE);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(new Color(0xE0, 0xE0, 0xE0));

        org.jfree.chart.renderer.category.LineAndShapeRenderer renderer =
                new org.jfree.chart.renderer.category.LineAndShapeRenderer();
        renderer.setSeriesPaint(0, BRAND_GREEN);
        renderer.setSeriesStroke(0, new java.awt.BasicStroke(2.5f));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6));
        renderer.setSeriesShapesFilled(0, true);
        renderer.setUseFillPaint(true);
        renderer.setSeriesFillPaint(0, Color.WHITE);
        renderer.setDefaultOutlinePaint(BRAND_GREEN);
        plot.setRenderer(renderer);

        org.jfree.chart.axis.CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 8));
        domainAxis.setAxisLineVisible(false);
        domainAxis.setTickMarksVisible(false);
        if (points.size() > 12) {
            domainAxis.setCategoryLabelPositions(
                    org.jfree.chart.axis.CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 4));
        }

        org.jfree.chart.axis.NumberAxis rangeAxis = (org.jfree.chart.axis.NumberAxis) plot.getRangeAxis();
        rangeAxis.setTickLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        rangeAxis.setLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        rangeAxis.setAxisLineVisible(false);
        rangeAxis.setTickMarksVisible(false);
        rangeAxis.setUpperMargin(0.12);

        return chart;
    }

    private JFreeChart buildCategoryHorizontalBarChart(List<CategoryRow> categoryRows) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (CategoryRow row : categoryRows) {
            dataset.addValue(row.emissions(), "kg CO2e",
                    prettifyCategory(row.category()) + "  (" + row.percentageShare() + "%)");
        }

        JFreeChart chart = ChartFactory.createBarChart(null, "", "kg CO2e", dataset, PlotOrientation.HORIZONTAL,
                false, false, false);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setBorderVisible(false);
        chart.setBackgroundPaint(Color.WHITE);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(new Color(0xE0, 0xE0, 0xE0));
        plot.setDomainGridlinesVisible(false);
        plot.setRangeAxisLocation(org.jfree.chart.axis.AxisLocation.BOTTOM_OR_LEFT);

        BarRenderer renderer = new BarRenderer() {
            @Override
            public java.awt.Paint getItemPaint(int row, int column) {
                return CATEGORY_PALETTE[column % CATEGORY_PALETTE.length];
            }
        };
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        renderer.setMaximumBarWidth(0.16);
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelGenerator(new org.jfree.chart.labels.StandardCategoryItemLabelGenerator(
                "{2}", new java.text.DecimalFormat("0.0")));
        renderer.setDefaultItemLabelFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 9));
        renderer.setDefaultPositiveItemLabelPosition(new org.jfree.chart.labels.ItemLabelPosition(
                org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE3, org.jfree.chart.ui.TextAnchor.CENTER_LEFT));
        plot.setRenderer(renderer);

        org.jfree.chart.axis.CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 9));
        domainAxis.setAxisLineVisible(false);
        domainAxis.setTickMarksVisible(false);

        org.jfree.chart.axis.NumberAxis rangeAxis = (org.jfree.chart.axis.NumberAxis) plot.getRangeAxis();
        rangeAxis.setTickLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 9));
        rangeAxis.setAxisLineVisible(false);
        rangeAxis.setTickMarksVisible(false);
        rangeAxis.setUpperMargin(0.18);

        return chart;
    }

    private JFreeChart buildOutcomeProgressChart(String rowLabel, long positive, long inProgress, long negative) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(positive, "Achieved / Completed", rowLabel);
        dataset.addValue(inProgress, "In Progress / Active", rowLabel);
        dataset.addValue(negative, "Not Achieved / Expired", rowLabel);

        JFreeChart chart = ChartFactory.createStackedBarChart(null, "", "", dataset, PlotOrientation.HORIZONTAL,
                true, false, false);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setBorderVisible(false);
        chart.setBackgroundPaint(Color.WHITE);
        chart.getLegend().setItemFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

        org.jfree.chart.renderer.category.StackedBarRenderer renderer =
                new org.jfree.chart.renderer.category.StackedBarRenderer();
        renderer.setSeriesPaint(0, OUTCOME_ACHIEVED);
        renderer.setSeriesPaint(1, OUTCOME_IN_PROGRESS);
        renderer.setSeriesPaint(2, OUTCOME_NOT_ACHIEVED);
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        renderer.setMaximumBarWidth(0.35);
        plot.setRenderer(renderer);

        plot.getDomainAxis().setTickLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getDomainAxis().setTickMarksVisible(false);
        plot.getRangeAxis().setVisible(false);

        return chart;
    }

    private byte[] buildCarbonFootprintExcel(CarbonFootprintReportData data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelStyles styles = createExcelStyles(workbook);

            Sheet summarySheet = workbook.createSheet("Summary");
            int rowIdx = 0;
            Row titleRow = summarySheet.createRow(rowIdx++);
            titleRow.createCell(0).setCellValue("EcoTrack — Sustainability Performance Report");
            titleRow.getCell(0).setCellStyle(styles.titleStyle());

            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "User",
                    data.user().getName() + " (" + data.user().getEmail() + ")", styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Reporting Period",
                    data.from().format(DATE_FMT) + " to " + data.to().format(DATE_FMT), styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Generated", LocalDate.now().format(DATE_FMT),
                    styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Total Emissions (kg CO2e)",
                    data.totalEmission().setScale(2, RoundingMode.HALF_UP).toString(), styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Previous Period Emissions (kg CO2e)",
                    data.previousTotalEmission().setScale(2, RoundingMode.HALF_UP).toString(), styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Change vs Previous Period",
                    data.percentChange() == null ? "N/A" : data.percentChange() + "%", styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Logged Activities", String.valueOf(data.entryCount()),
                    styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Goals Achieved",
                    data.goalData().achievedCount() + " / " + data.goalData().totalGoals(), styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Challenges Completed",
                    data.challengeData().completedCount() + " / " + data.challengeData().totalJoined(),
                    styles.labelStyle());
            rowIdx = writeLabelValueRow(summarySheet, rowIdx, "Total XP Earned",
                    String.valueOf(data.totalXpEarned()), styles.labelStyle());
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            Sheet analysisSheet = workbook.createSheet("Carbon Analysis");
            writeHeaderRow(analysisSheet, 0, new String[] { "Category", "Emissions (kg CO2e)", "Share (%)" },
                    styles.headerStyle());
            int catRow = 1;
            for (CategoryRow row : data.categoryRows()) {
                Row r = analysisSheet.createRow(catRow++);
                r.createCell(0).setCellValue(prettifyCategory(row.category()));
                r.createCell(1).setCellValue(row.emissions().setScale(2, RoundingMode.HALF_UP).doubleValue());
                r.createCell(2).setCellValue(row.percentageShare());
            }
            for (int i = 0; i < 3; i++) {
                analysisSheet.autoSizeColumn(i);
            }

            Sheet entriesSheet = workbook.createSheet("Carbon Entries");
            writeCarbonEntriesTable(entriesSheet, 0, data.entries(), styles.headerStyle());

            Sheet goalsSheet = workbook.createSheet("Goals");
            writeGoalTable(goalsSheet, 0, data.goalData().rows(), styles.headerStyle());

            Sheet challengesSheet = workbook.createSheet("Challenges");
            int chRow = 0;
            Row chTitle = challengesSheet.createRow(chRow++);
            chTitle.createCell(0).setCellValue("Activity Challenges");
            chTitle.getCell(0).setCellStyle(styles.labelStyle());
            chRow++;
            writeActivityChallengeTable(challengesSheet, chRow, data.challengeData().activityRows(),
                    styles.headerStyle());
            chRow += data.challengeData().activityRows().size() + 3;
            Row carbonChTitle = challengesSheet.createRow(chRow++);
            carbonChTitle.createCell(0).setCellValue("Carbon Reduction Challenges");
            carbonChTitle.getCell(0).setCellStyle(styles.labelStyle());
            chRow++;
            writeCarbonChallengeTable(challengesSheet, chRow, data.challengeData().carbonRows(),
                    styles.headerStyle());

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel report: " + e.getMessage(), e);
        }
    }

    private byte[] buildCarbonFootprintCsv(CarbonFootprintReportData data) {
        StringBuilder csv = new StringBuilder();

        csvRow(csv, "EcoTrack — Sustainability Performance Report");
        csvRow(csv, "User", data.user().getName() + " (" + data.user().getEmail() + ")");
        csvRow(csv, "Reporting Period", data.from().format(DATE_FMT) + " to " + data.to().format(DATE_FMT));
        csvRow(csv, "Generated", LocalDate.now().format(DATE_FMT));
        csvRow(csv, "Total Emissions (kg CO2e)", data.totalEmission().setScale(2, RoundingMode.HALF_UP).toString());
        csvRow(csv, "Previous Period Emissions (kg CO2e)",
                data.previousTotalEmission().setScale(2, RoundingMode.HALF_UP).toString());
        csvRow(csv, "Change vs Previous Period", data.percentChange() == null ? "N/A" : data.percentChange() + "%");
        csvRow(csv, "Logged Activities", String.valueOf(data.entryCount()));
        csvRow(csv, "Goals Achieved", data.goalData().achievedCount() + " / " + data.goalData().totalGoals());
        csvRow(csv, "Challenges Completed",
                data.challengeData().completedCount() + " / " + data.challengeData().totalJoined());
        csvRow(csv, "Total XP Earned", String.valueOf(data.totalXpEarned()));
        csv.append('\n');

        csvRow(csv, "Emissions Trend");
        csvRow(csv, "Period", "Emissions (kg CO2e)");
        for (TrendPoint p : data.trendPoints()) {
            csvRow(csv, p.label(), p.emission().setScale(2, RoundingMode.HALF_UP).toString());
        }
        csv.append('\n');

        csvRow(csv, "Emissions by Category");
        csvRow(csv, "Category", "Emissions (kg CO2e)", "Share (%)");
        for (CategoryRow row : data.categoryRows()) {
            csvRow(csv, prettifyCategory(row.category()),
                    row.emissions().setScale(2, RoundingMode.HALF_UP).toString(), String.valueOf(row.percentageShare()));
        }
        csv.append('\n');

        csvRow(csv, "Carbon Entries");
        csvRow(csv, "Date", "Category", "Amount", "Unit", "Emission (kg CO2e)");
        for (CarbonEntry entry : data.entries()) {
            csvRow(csv, entry.getActivityDate().format(DATE_FMT), prettifyCategory(entry.getCategory().name()),
                    entry.getAmount().toString(), entry.getUnit(),
                    entry.getCarbonEmission().setScale(2, RoundingMode.HALF_UP).toString());
        }
        csv.append('\n');

        csvRow(csv, "Goals");
        csvRow(csv, "Goal", "Type", "Outcome", "Budget (Target)", "Emitted", "Budget Used (%)", "XP Earned",
                "Start Date", "End Date");
        for (GoalReportRow row : data.goalData().rows()) {
            GoalResponse g = row.goal();
            csvRow(csv, g.getGoalName(), prettifyCategory(g.getGoalType()), row.outcome(),
                    g.getTargetValue().toString(),
                    row.currentValue() != null ? row.currentValue().toString() : "N/A",
                    row.budgetUsedPercent() != null ? row.budgetUsedPercent().toString() : "N/A",
                    String.valueOf(row.xpEarned()), g.getStartDate().format(DATE_FMT), g.getEndDate().format(DATE_FMT));
        }
        csv.append('\n');

        csvRow(csv, "Activity Challenges");
        csvRow(csv, "Challenge", "Category", "Status", "Progress (%)", "Current Value", "Target", "Joined At",
                "Completed/Resolved At");
        for (UserChallengeResponse c : data.challengeData().activityRows()) {
            csvRow(csv, c.getChallenge().getTitle(), prettifyCategory(c.getChallenge().getCategory()),
                    c.getUserStatus(),
                    c.getCompletionPercentage() != null ? c.getCompletionPercentage().toString() : "0",
                    c.getCurrentValue() != null ? c.getCurrentValue().toString() : "0",
                    c.getChallenge().getTargetValue() != null ? c.getChallenge().getTargetValue().toString() : "0",
                    c.getJoinedAt() != null ? c.getJoinedAt().toLocalDate().format(DATE_FMT) : "N/A",
                    c.getCompletedAt() != null ? c.getCompletedAt().toLocalDate().format(DATE_FMT) : "N/A");
        }
        csv.append('\n');

        csvRow(csv, "Carbon Reduction Challenges");
        csvRow(csv, "Challenge", "Category", "Status", "Budget (kg CO2e)", "Used (kg CO2e)", "Remaining (kg CO2e)",
                "Remaining (%)", "Joined At", "Completed/Resolved At");
        for (UserChallengeResponse c : data.challengeData().carbonRows()) {
            BigDecimal remainingKg = remainingBudget(c.getEffectiveTarget(), c.getCurrentValue());
            csvRow(csv, c.getChallenge().getTitle(), prettifyCategory(c.getChallenge().getCategory()),
                    c.getUserStatus(),
                    c.getEffectiveTarget() != null ? c.getEffectiveTarget().toString() : "0",
                    c.getCurrentValue() != null ? c.getCurrentValue().toString() : "0",
                    remainingKg != null ? remainingKg.toString() : "0",
                    c.getCompletionPercentage() != null ? c.getCompletionPercentage().toString() : "0",
                    c.getJoinedAt() != null ? c.getJoinedAt().toLocalDate().format(DATE_FMT) : "N/A",
                    c.getCompletedAt() != null ? c.getCompletedAt().toLocalDate().format(DATE_FMT) : "N/A");
        }

        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] body = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }

    private void csvRow(StringBuilder sb, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvEscape(fields[i]));
        }
        sb.append('\n');
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private GoalReportRow toReportRow(Long userId, GoalResponse goal) {
        String outcome = deriveGoalOutcome(goal.getStatus());
        int xpEarned = xpEarnedForGoal(goal);
        try {
            GoalProgressResponse progress = goalService.calculateProgress(userId, goal.getGoalId());
            return new GoalReportRow(goal, progress.getCurrentValue(), progress.getCompletionPercentage(),
                    outcome, xpEarned);
        } catch (IllegalArgumentException ex) {
            return new GoalReportRow(goal, null, null, outcome, xpEarned);
        }
    }

    private String deriveGoalOutcome(String status) {
        if ("COMPLETED".equalsIgnoreCase(status))
            return "Achieved";
        if ("EXPIRED".equalsIgnoreCase(status))
            return "Not Achieved";
        if ("ACTIVE".equalsIgnoreCase(status))
            return "In Progress";
        return status;
    }

    private int xpEarnedForGoal(GoalResponse g) {
        if (!"COMPLETED".equalsIgnoreCase(g.getStatus()))
            return 0;
        return g.getXpReward() != null ? g.getXpReward() : 100;
    }

    private GoalReportData buildGoalReportData(User user, List<GoalReportRow> rows) {
        int total = rows.size();
        long achievedCount = rows.stream().filter(r -> "Achieved".equals(r.outcome())).count();
        long notAchievedCount = rows.stream().filter(r -> "Not Achieved".equals(r.outcome())).count();
        long inProgressCount = rows.stream().filter(r -> "In Progress".equals(r.outcome())).count();

        BigDecimal successRate = total > 0
                ? BigDecimal.valueOf(achievedCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int totalXpEarned = rows.stream().mapToInt(GoalReportRow::xpEarned).sum();

        return new GoalReportData(user, total, achievedCount, notAchievedCount, inProgressCount,
                successRate, totalXpEarned, rows);
    }

    private ChallengeReportData buildChallengeReportData(User user, List<UserChallengeResponse> joined) {
        int total = joined.size();
        long completedCount = joined.stream().filter(c -> "COMPLETED".equals(c.getUserStatus())).count();
        long expiredCount = joined.stream().filter(c -> "EXPIRED".equals(c.getUserStatus())).count();
        long cancelledCount = joined.stream().filter(c -> "CANCELLED".equals(c.getUserStatus())).count();
        long inProgressCount = total - completedCount - expiredCount - cancelledCount;

        int totalXpEarned = joined.stream()
                .filter(c -> "COMPLETED".equals(c.getUserStatus()))
                .mapToInt(c -> c.getChallenge().getRewardXp() != null ? c.getChallenge().getRewardXp() : 0)
                .sum();

        List<UserChallengeResponse> carbonRows = joined.stream()
                .filter(c -> "CARBON_REDUCTION".equals(c.getChallenge().getChallengeType()))
                .toList();
        List<UserChallengeResponse> activityRows = joined.stream()
                .filter(c -> !"CARBON_REDUCTION".equals(c.getChallenge().getChallengeType()))
                .toList();

        return new ChallengeReportData(user, total, completedCount, expiredCount, cancelledCount, inProgressCount,
                totalXpEarned, activityRows, carbonRows);
    }

    private BigDecimal remainingBudget(BigDecimal effectiveTarget, BigDecimal currentValue) {
        if (effectiveTarget == null || currentValue == null) {
            return null;
        }
        BigDecimal remaining = effectiveTarget.subtract(currentValue);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    private void addVectorChart(PdfWriter writer, Document document, JFreeChart chart, float displayWidth,
            float displayHeight) throws DocumentException {
        com.lowagie.text.pdf.PdfContentByte canvas = writer.getDirectContent();
        com.lowagie.text.pdf.PdfTemplate template = canvas.createTemplate(displayWidth, displayHeight);

        java.awt.Graphics2D g2 = template.createGraphics(displayWidth, displayHeight,
                new com.lowagie.text.pdf.DefaultFontMapper());
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL,
                java.awt.RenderingHints.VALUE_STROKE_PURE);
        try {
            java.awt.geom.Rectangle2D chartArea = new java.awt.geom.Rectangle2D.Float(0, 0, displayWidth,
                    displayHeight);
            chart.draw(g2, chartArea);
        } finally {
            g2.dispose();
        }

        com.lowagie.text.Image vectorImage = com.lowagie.text.Image.getInstance(template);
        vectorImage.setAlignment(com.lowagie.text.Image.ALIGN_CENTER);

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingAfter(14);
        PdfPCell cell = new PdfPCell();
        cell.setPadding(14);
        cell.setBorderColor(new Color(0xDD, 0xDD, 0xDD));
        cell.setBorderWidth(0.75f);
        cell.setBackgroundColor(Color.WHITE);
        cell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
        cell.addElement(vectorImage);
        box.addCell(cell);
        document.add(box);
    }

    private static final Color[] CATEGORY_PALETTE = {
            BRAND_GREEN, new Color(0x81, 0xC7, 0x84), new Color(0x2A, 0x5C, 0xA0),
            new Color(0xE0, 0xA5, 0x3C), new Color(0xB0, 0x2A, 0x2A), new Color(0x7A, 0x5C, 0xA0)
    };

    private void addDivider(Document document) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BRAND_GREEN);
        cell.setBorderWidth(1.5f);
        cell.setFixedHeight(2);
        line.addCell(cell);
        document.add(line);
        document.add(Chunk.NEWLINE);
    }

    private PdfPCell kpiCell(String label, String value, Font valueFont, Font labelFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(12);
        cell.setBackgroundColor(LIGHT_GREEN);
        cell.setBorderColor(Color.WHITE);
        cell.setBorderWidth(4);

        Paragraph p = new Paragraph();
        p.add(new Chunk(value + "\n", valueFont));
        p.add(new Chunk(label, labelFont));
        cell.addElement(p);
        return cell;
    }

    private PdfPCell dataCell(String text, Font font, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(sanitizeForPdf(text), font));
        cell.setPadding(8);
        cell.setBackgroundColor(background);
        return cell;
    }

    private String sanitizeForPdf(String text) {
        if (text == null) {
            return "";
        }
        String subscripts = "₀₁₂₃₄₅₆₇₈₉";
        String superscripts = "⁰¹²³⁴⁵⁶⁷⁸⁹";
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            int subIdx = subscripts.indexOf(c);
            int supIdx = superscripts.indexOf(c);
            if (subIdx >= 0) {
                result.append((char) ('0' + subIdx));
            } else if (supIdx >= 0) {
                result.append((char) ('0' + supIdx));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private ExcelStyles createExcelStyles(Workbook workbook) {
        CellStyle titleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(IndexedColors.DARK_GREEN.getIndex());
        titleStyle.setFont(titleFont);

        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle labelStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font labelFont = workbook.createFont();
        labelFont.setBold(true);
        labelStyle.setFont(labelFont);

        return new ExcelStyles(titleStyle, headerStyle, labelStyle);
    }

    private void writeHeaderRow(Sheet sheet, int rowIdx, String[] headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(rowIdx);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private int writeLabelValueRow(Sheet sheet, int rowIdx, String label, String value, CellStyle labelStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label + ":");
        labelCell.setCellStyle(labelStyle);
        row.createCell(1).setCellValue(value);
        return rowIdx + 1;
    }

    private void writeGoalTable(Sheet sheet, int startRow, List<GoalReportRow> rows, CellStyle headerStyle) {
        String[] headers = { "Goal", "Type", "Outcome", "Budget (Target)", "Emitted", "Budget Used (%)",
                "XP Earned", "Start Date", "End Date" };
        writeHeaderRow(sheet, startRow, headers, headerStyle);

        int rowIdx = startRow + 1;
        for (GoalReportRow row : rows) {
            GoalResponse g = row.goal();
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(g.getGoalName());
            r.createCell(1).setCellValue(prettifyCategory(g.getGoalType()));
            r.createCell(2).setCellValue(row.outcome());
            r.createCell(3).setCellValue(g.getTargetValue().doubleValue());

            if (row.currentValue() != null) {
                r.createCell(4).setCellValue(row.currentValue().doubleValue());
            } else {
                r.createCell(4).setCellValue("N/A");
            }
            if (row.budgetUsedPercent() != null) {
                r.createCell(5).setCellValue(row.budgetUsedPercent().doubleValue());
            } else {
                r.createCell(5).setCellValue("N/A");
            }

            r.createCell(6).setCellValue(row.xpEarned());
            r.createCell(7).setCellValue(g.getStartDate().format(DATE_FMT));
            r.createCell(8).setCellValue(g.getEndDate().format(DATE_FMT));
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeActivityChallengeTable(Sheet sheet, int startRow, List<UserChallengeResponse> rows,
            CellStyle headerStyle) {
        String[] headers = { "Challenge", "Category", "Status", "Progress (%)", "Current Value", "Target",
                "Joined At", "Completed/Resolved At" };
        writeHeaderRow(sheet, startRow, headers, headerStyle);

        int rowIdx = startRow + 1;
        for (UserChallengeResponse c : rows) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(c.getChallenge().getTitle());
            r.createCell(1).setCellValue(prettifyCategory(c.getChallenge().getCategory()));
            r.createCell(2).setCellValue(c.getUserStatus());
            r.createCell(3).setCellValue(
                    c.getCompletionPercentage() != null ? c.getCompletionPercentage().doubleValue() : 0.0);
            r.createCell(4).setCellValue(c.getCurrentValue() != null ? c.getCurrentValue().doubleValue() : 0.0);
            r.createCell(5).setCellValue(
                    c.getChallenge().getTargetValue() != null ? c.getChallenge().getTargetValue().doubleValue() : 0.0);
            r.createCell(6)
                    .setCellValue(c.getJoinedAt() != null ? c.getJoinedAt().toLocalDate().format(DATE_FMT) : "N/A");
            r.createCell(7).setCellValue(
                    c.getCompletedAt() != null ? c.getCompletedAt().toLocalDate().format(DATE_FMT) : "N/A");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeCarbonChallengeTable(Sheet sheet, int startRow, List<UserChallengeResponse> rows,
            CellStyle headerStyle) {
        String[] headers = { "Challenge", "Category", "Status", "Budget (kg CO2e)", "Used (kg CO2e)",
                "Remaining (kg CO2e)", "Remaining (%)", "Joined At", "Completed/Resolved At" };
        writeHeaderRow(sheet, startRow, headers, headerStyle);

        int rowIdx = startRow + 1;
        for (UserChallengeResponse c : rows) {
            Row r = sheet.createRow(rowIdx++);
            BigDecimal remainingKg = remainingBudget(c.getEffectiveTarget(), c.getCurrentValue());
            r.createCell(0).setCellValue(c.getChallenge().getTitle());
            r.createCell(1).setCellValue(prettifyCategory(c.getChallenge().getCategory()));
            r.createCell(2).setCellValue(c.getUserStatus());
            r.createCell(3).setCellValue(c.getEffectiveTarget() != null ? c.getEffectiveTarget().doubleValue() : 0.0);
            r.createCell(4).setCellValue(c.getCurrentValue() != null ? c.getCurrentValue().doubleValue() : 0.0);
            r.createCell(5).setCellValue(remainingKg != null ? remainingKg.doubleValue() : 0.0);
            r.createCell(6).setCellValue(
                    c.getCompletionPercentage() != null ? c.getCompletionPercentage().doubleValue() : 0.0);
            r.createCell(7)
                    .setCellValue(c.getJoinedAt() != null ? c.getJoinedAt().toLocalDate().format(DATE_FMT) : "N/A");
            r.createCell(8).setCellValue(
                    c.getCompletedAt() != null ? c.getCompletedAt().toLocalDate().format(DATE_FMT) : "N/A");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeCarbonEntriesTable(Sheet sheet, int startRow, List<CarbonEntry> entries, CellStyle headerStyle) {
        String[] headers = { "Date", "Category", "Amount", "Unit", "Emission (kg CO2e)" };
        writeHeaderRow(sheet, startRow, headers, headerStyle);

        int rowIdx = startRow + 1;
        for (CarbonEntry entry : entries) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(entry.getActivityDate().format(DATE_FMT));
            r.createCell(1).setCellValue(prettifyCategory(entry.getCategory().name()));
            r.createCell(2).setCellValue(entry.getAmount().doubleValue());
            r.createCell(3).setCellValue(entry.getUnit());
            r.createCell(4).setCellValue(entry.getCarbonEmission().setScale(2, RoundingMode.HALF_UP).doubleValue());
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String formatKg(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " kg CO2e";
    }

    private String prettifyCategory(String rawCategory) {
        String lower = rawCategory.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record ExcelStyles(CellStyle titleStyle, CellStyle headerStyle, CellStyle labelStyle) {
    }

    private record CarbonFootprintReportData(
            User user,
            LocalDate from,
            LocalDate to,
            BigDecimal totalEmission,
            int entryCount,
            List<CategoryRow> categoryRows,
            BigDecimal previousTotalEmission,
            BigDecimal percentChange,
            List<TrendPoint> trendPoints,
            GoalReportData goalData,
            ChallengeReportData challengeData,
            int totalXpEarned,
            List<String> insights,
            List<String> recommendations,
            List<CarbonEntry> entries) {
    }

    private record CategoryRow(String category, BigDecimal emissions, double percentageShare) {
    }

    private record TrendPoint(String label, BigDecimal emission) {
    }

    private record GoalReportData(
            User user,
            int totalGoals,
            long achievedCount,
            long notAchievedCount,
            long inProgressCount,
            BigDecimal successRate,
            int totalXpEarned,
            List<GoalReportRow> rows) {
    }

    private record GoalReportRow(
            GoalResponse goal,
            BigDecimal currentValue,
            BigDecimal budgetUsedPercent,
            String outcome,
            int xpEarned) {
    }

    private record ChallengeReportData(
            User user,
            int totalJoined,
            long completedCount,
            long expiredCount,
            long cancelledCount,
            long inProgressCount,
            int totalXpEarned,
            List<UserChallengeResponse> activityRows,
            List<UserChallengeResponse> carbonRows) {
    }
}
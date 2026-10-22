package com.fiap.feedback.service;

import com.fiap.feedback.dto.WeeklyFeedbackReport;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class WeeklyFeedbackReportService {
    private final FeedbackRepository repository;

    public WeeklyFeedbackReportService(FeedbackRepository repository) {
        this.repository = repository;
    }

    public WeeklyFeedbackReport generatePreviousWeekReport() {
        return generateReportForReferenceDate(LocalDate.now(ZoneOffset.UTC));
    }

    WeeklyFeedbackReport generateReportForReferenceDate(LocalDate referenceDate) {
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate weekEndExclusive = weekEnd.plusDays(1);

        List<Feedback> weeklyFeedbacks = new ArrayList<>();
        Map<LocalDate, Long> feedbackCountByDay = new TreeMap<>();
        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            feedbackCountByDay.put(date, 0L);
        }

        Map<Urgencia, Long> feedbackCountByUrgency = new EnumMap<>(Urgencia.class);
        for (Urgencia urgencia : Urgencia.values()) {
            feedbackCountByUrgency.put(urgencia, 0L);
        }

        for (Feedback feedback : repository.findAll()) {
            LocalDate feedbackDate = parseFeedbackDate(feedback);
            if (feedbackDate.isBefore(weekStart) || !feedbackDate.isBefore(weekEndExclusive)) {
                continue;
            }

            weeklyFeedbacks.add(feedback);
            feedbackCountByDay.compute(feedbackDate, (date, count) -> count + 1);
            feedbackCountByUrgency.compute(feedback.getUrgencia(), (urgencia, count) -> count + 1);
        }

        double averageRating = weeklyFeedbacks.stream()
                .map(Feedback::getNota)
                .filter(rating -> rating != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        return new WeeklyFeedbackReport(
                weekStart,
                weekEnd,
                weeklyFeedbacks.size(),
                averageRating,
                List.copyOf(weeklyFeedbacks),
                Collections.unmodifiableMap(new TreeMap<>(feedbackCountByDay)),
                Collections.unmodifiableMap(new EnumMap<>(feedbackCountByUrgency))
        );
    }

    private LocalDate parseFeedbackDate(Feedback feedback) {
        try {
            return LocalDateTime.parse(feedback.getDataEnvio()).toLocalDate();
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalStateException(
                    "Data de envio inválida no feedback " + feedback.getId(),
                    exception
            );
        }
    }
}

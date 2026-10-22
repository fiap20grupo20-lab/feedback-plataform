package com.fiap.feedback.dto;

import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeeklyFeedbackReport(
        LocalDate weekStart,
        LocalDate weekEnd,
        long totalFeedbacks,
        double averageRating,
        List<Feedback> feedbacks,
        Map<LocalDate, Long> feedbackCountByDay,
        Map<Urgencia, Long> feedbackCountByUrgency
) {
}

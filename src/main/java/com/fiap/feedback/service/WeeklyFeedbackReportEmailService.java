package com.fiap.feedback.service;

import com.fiap.feedback.dto.WeeklyFeedbackReport;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class WeeklyFeedbackReportEmailService {
    private final FeedbackEmailService emailService;

    public WeeklyFeedbackReportEmailService(FeedbackEmailService emailService) {
        this.emailService = emailService;
    }

    public void send(WeeklyFeedbackReport report) {
        StringBuilder body = new StringBuilder()
                .append("Relatório semanal de feedbacks\n")
                .append("Período: ").append(report.weekStart()).append(" a ").append(report.weekEnd()).append('\n')
                .append("Total de avaliações: ").append(report.totalFeedbacks()).append('\n')
                .append("Média das avaliações: ")
                .append(String.format(Locale.ROOT, "%.2f", report.averageRating()))
                .append("\n\nQuantidade por dia:\n");

        report.feedbackCountByDay().forEach((date, count) ->
                body.append(date).append(": ").append(count).append('\n'));
        body.append("\nQuantidade por urgência:\n");
        for (Urgencia urgency : Urgencia.values()) {
            body.append(urgency).append(": ")
                    .append(report.feedbackCountByUrgency().getOrDefault(urgency, 0L))
                    .append('\n');
        }

        body.append("\nFeedbacks:\n");
        for (Feedback feedback : report.feedbacks()) {
            body.append("- Descrição: ").append(feedback.getDescricao())
                    .append("; Urgência: ").append(feedback.getUrgencia())
                    .append("; Data de envio: ").append(feedback.getDataEnvio())
                    .append('\n');
        }

        emailService.send(
                "Relatório semanal de feedbacks: " + report.weekStart() + " a " + report.weekEnd(),
                body.toString()
        );
    }
}

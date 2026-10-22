package com.fiap.feedback.service;

import com.fiap.feedback.dto.WeeklyFeedbackReport;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyFeedbackReportServiceTest {

    private final FeedbackRepository repository = mock(FeedbackRepository.class);
    private final WeeklyFeedbackReportService service = new WeeklyFeedbackReportService(repository);

    @Test
    void deveGerarRelatorioDaSemanaCompletaAnterior() {
        when(repository.findAll()).thenReturn(List.of(
                feedback("1", "2026-10-05T10:00:00", 8, Urgencia.ALTA),
                feedback("2", "2026-10-05T11:00:00", 6, Urgencia.MEDIA),
                feedback("3", "2026-10-11T23:59:00", 10, Urgencia.BAIXA),
                feedback("4", "2026-10-12T00:00:00", 1, Urgencia.ALTA)
        ));

        WeeklyFeedbackReport report = service.generateReportForReferenceDate(LocalDate.of(2026, 10, 17));

        assertEquals(LocalDate.of(2026, 10, 5), report.weekStart());
        assertEquals(LocalDate.of(2026, 10, 11), report.weekEnd());
        assertEquals(3, report.totalFeedbacks());
        assertEquals(8.0, report.averageRating());
        assertEquals(2, report.feedbackCountByDay().get(LocalDate.of(2026, 10, 5)));
        assertEquals(0, report.feedbackCountByDay().get(LocalDate.of(2026, 10, 6)));
        assertEquals(1, report.feedbackCountByUrgency().get(Urgencia.ALTA));
        assertEquals(1, report.feedbackCountByUrgency().get(Urgencia.MEDIA));
        assertEquals(1, report.feedbackCountByUrgency().get(Urgencia.BAIXA));
    }

    private Feedback feedback(String id, String dataEnvio, int nota, Urgencia urgencia) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setDataEnvio(dataEnvio);
        feedback.setNota(nota);
        feedback.setUrgencia(urgencia);
        return feedback;
    }
}

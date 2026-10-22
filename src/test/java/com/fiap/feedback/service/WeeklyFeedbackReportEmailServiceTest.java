package com.fiap.feedback.service;

import com.fiap.feedback.dto.WeeklyFeedbackReport;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WeeklyFeedbackReportEmailServiceTest {

    private final FeedbackEmailService emailService = mock(FeedbackEmailService.class);
    private final WeeklyFeedbackReportEmailService service =
            new WeeklyFeedbackReportEmailService(emailService);

    @Test
    void deveEnviarRelatorioComAvaliacoesEAgregacoes() {
        Feedback feedback = new Feedback();
        feedback.setDescricao("Atendimento excelente");
        feedback.setUrgencia(Urgencia.ALTA);
        feedback.setDataEnvio("2026-10-05T10:00:00");
        WeeklyFeedbackReport report = new WeeklyFeedbackReport(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 11),
                1,
                9.0,
                List.of(feedback),
                Map.of(LocalDate.of(2026, 10, 5), 1L),
                Map.of(Urgencia.ALTA, 1L)
        );

        service.send(report);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(subjectCaptor.capture(), bodyCaptor.capture());
        assertTrue(subjectCaptor.getValue().contains("2026-10-05 a 2026-10-11"));
        assertTrue(bodyCaptor.getValue().contains("Média das avaliações: 9.00"));
        assertTrue(bodyCaptor.getValue().contains("2026-10-05: 1"));
        assertTrue(bodyCaptor.getValue().contains("ALTA: 1"));
        assertTrue(bodyCaptor.getValue().contains("Descrição: Atendimento excelente"));
        assertTrue(bodyCaptor.getValue().contains("Data de envio: 2026-10-05T10:00:00"));
    }
}

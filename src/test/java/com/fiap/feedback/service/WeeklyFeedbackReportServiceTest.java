package com.fiap.feedback.service;

import com.fiap.feedback.dto.WeeklyFeedbackReport;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test
    void deveGerarRelatorioVazioQuandoNaoExistiremFeedbacksNaSemana() {
        when(repository.findAll()).thenReturn(List.of());

        WeeklyFeedbackReport report =
                service.generateReportForReferenceDate(LocalDate.of(2026, 10, 17));

        assertEquals(0, report.totalFeedbacks());
        assertEquals(0.0, report.averageRating());

        assertEquals(7, report.feedbackCountByDay().size());

        assertTrue(report.feedbackCountByDay().values()
                .stream().allMatch(count -> count == 0));

        assertEquals(3, report.feedbackCountByUrgency().size());

        assertTrue(report.feedbackCountByUrgency().values()
                .stream().allMatch(count -> count == 0));

        verify(repository, times(1)).findAll();
    }

    @Test
    void deveGerarRelatorioComUmaUnicaAvaliacao() {
        when(repository.findAll()).thenReturn(List.of(
                feedback("1", "2026-10-07T14:30:00", 9, Urgencia.ALTA)
        ));

        WeeklyFeedbackReport report =
                service.generateReportForReferenceDate(LocalDate.of(2026, 10, 17));

        assertEquals(1, report.totalFeedbacks());
        assertEquals(9.0, report.averageRating());
        assertEquals(1, report.feedbackCountByDay().get(LocalDate.of(2026, 10, 7)));
        assertEquals(1, report.feedbackCountByUrgency().get(Urgencia.ALTA));

        verify(repository, times(1)).findAll();
    }

    @Test
    void deveIncluirFeedbacksExatamenteNoInicioDaSemana() {
        when(repository.findAll()).thenReturn(List.of(
                feedback("1", "2026-10-05T00:00:00", 8, Urgencia.MEDIA),
                feedback("2", "2026-10-04T23:59:59", 5, Urgencia.ALTA)
        ));

        WeeklyFeedbackReport report =
                service.generateReportForReferenceDate(LocalDate.of(2026, 10, 17));

        assertEquals(1, report.totalFeedbacks());
        assertEquals(1, report.feedbackCountByDay().get(LocalDate.of(2026, 10, 5)));
        assertEquals(8.0, report.averageRating());

        verify(repository, times(1)).findAll();
    }

    @Test
    void deveIgnorarNotaNulaNoCalculoDaMedia() {
        when(repository.findAll()).thenReturn(List.of(
                feedback("1", "2026-10-05T10:00:00", null, Urgencia.ALTA),
                feedback("2", "2026-10-06T10:00:00", 8, Urgencia.MEDIA)
        ));

        WeeklyFeedbackReport report =
                service.generateReportForReferenceDate(LocalDate.of(2026, 10, 17));

        assertEquals(2, report.totalFeedbacks());
        assertEquals(8.0, report.averageRating());

        verify(repository, times(1)).findAll();
    }

    @Test
    void deveRetornarMediaZeroQuandoTodasAsNotasForemNulas() {
        when(repository.findAll()).thenReturn(List.of(
                feedback("1", "2026-10-05T10:00:00", null, Urgencia.ALTA)
        ));

        WeeklyFeedbackReport report =
                service.generateReportForReferenceDate(LocalDate.of(2026, 10, 17));

        assertEquals(1, report.totalFeedbacks());
        assertEquals(0.0, report.averageRating());

        verify(repository, times(1)).findAll();
    }

    @Test
    void deveLancarExcecaoQuandoDataDoFeedbackForInvalida() {
        when(repository.findAll()).thenReturn(List.of(
                feedback("1", "data-invalida", 8, Urgencia.ALTA)
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.generateReportForReferenceDate(
                        LocalDate.of(2026, 10, 17)
                )
        );

        assertTrue(exception.getMessage().contains("1"));
        assertInstanceOf(
                java.time.format.DateTimeParseException.class,
                exception.getCause()
        );

        verify(repository, times(1)).findAll();
    }

    @Test
    void deveLancarExcecaoQuandoDataDoFeedbackForNula() {
        when(repository.findAll()).thenReturn(List.of(
                feedback("2", null, 8, Urgencia.MEDIA)
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.generateReportForReferenceDate(
                        LocalDate.of(2026, 10, 17)
                )
        );

        assertTrue(exception.getMessage().contains("2"));
        assertInstanceOf(NullPointerException.class, exception.getCause());

        verify(repository, times(1)).findAll();
    }

    @Test
    void deveRetornarMapasImutaveisNoRelatorio() {
        when(repository.findAll()).thenReturn(List.of(
                feedback("1", "2026-10-05T10:00:00", 8, Urgencia.ALTA)
        ));

        WeeklyFeedbackReport report =
                service.generateReportForReferenceDate(LocalDate.of(2026, 10, 17));

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.feedbackCountByDay()
                        .put(LocalDate.of(2026, 10, 5), 10L)
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.feedbackCountByUrgency()
                        .put(Urgencia.ALTA, 10L)
        );

        verify(repository, times(1)).findAll();
    }

    @Test
    void deveGerarRelatorioDaSemanaAnteriorConsiderandoDataAtualUtc() {
        when(repository.findAll()).thenReturn(List.of());

        LocalDate hojeUtc = LocalDate.now(ZoneOffset.UTC);

        WeeklyFeedbackReport report = service.generatePreviousWeekReport();

        LocalDate segundaEsperada = hojeUtc
                .with(java.time.temporal.TemporalAdjusters
                        .previousOrSame(java.time.DayOfWeek.MONDAY))
                .minusWeeks(1);

        assertEquals(segundaEsperada, report.weekStart());
        assertEquals(segundaEsperada.plusDays(6), report.weekEnd());
        assertEquals(0, report.totalFeedbacks());

        verify(repository, times(1)).findAll();
    }

    private Feedback feedback(
            String id,
            String dataEnvio,
            Integer nota,
            Urgencia urgencia
    ) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setDataEnvio(dataEnvio);
        feedback.setNota(nota);
        feedback.setUrgencia(urgencia);
        return feedback;
    }
}

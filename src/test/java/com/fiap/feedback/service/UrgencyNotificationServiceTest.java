package com.fiap.feedback.service;

import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UrgencyNotificationServiceTest {

    private final FeedbackEmailService emailService = mock(FeedbackEmailService.class);
    private final UrgencyNotificationService service =
            new UrgencyNotificationService(emailService);

    @Test
    void deveEnviarEmailQuandoUrgenciaForAlta() {
        Feedback feedback = feedback(Urgencia.ALTA);

        service.notifyIfUrgent(feedback);

        verify(emailService).send(
                "Feedback recebido com urgência alta",
                """
                        Um feedback de urgência alta foi recebido.

                        Descrição: Falha crítica
                        Urgência: ALTA
                        Data de envio: 2026-10-17T10:00:00
                        """
        );
    }

    @Test
    void naoDeveEnviarEmailParaUrgenciaBaixa() {
        service.notifyIfUrgent(feedback(Urgencia.BAIXA));

        verifyNoInteractions(emailService);
    }

    private Feedback feedback(Urgencia urgencia) {
        Feedback feedback = new Feedback();
        feedback.setId("feedback-1");
        feedback.setDescricao("Falha crítica");
        feedback.setUrgencia(urgencia);
        feedback.setDataEnvio("2026-10-17T10:00:00");
        return feedback;
    }
}

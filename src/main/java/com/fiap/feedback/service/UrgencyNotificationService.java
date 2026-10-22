package com.fiap.feedback.service;

import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import org.springframework.stereotype.Service;

@Service
public class UrgencyNotificationService {
    private final FeedbackEmailService emailService;

    public UrgencyNotificationService(FeedbackEmailService emailService) {
        this.emailService = emailService;
    }

    public void notifyIfUrgent(Feedback feedback) {
        if (feedback.getUrgencia() != Urgencia.ALTA) {
            return;
        }
        emailService.send(
                "Feedback recebido com urgência alta",
                """
                Um feedback de urgência alta foi recebido.

                Descrição: %s
                Urgência: %s
                Data de envio: %s
                """.formatted(
                feedback.getDescricao(),
                feedback.getUrgencia(),
                feedback.getDataEnvio()
                )
        );
    }
}

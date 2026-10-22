package com.fiap.feedback.service;

import com.azure.communication.email.models.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeedbackEmailServiceTest {

    private final AzureCommunicationEmailSender emailSender = mock(AzureCommunicationEmailSender.class);

    @Test
    void deveEnviarEmailComConfiguracaoValida() {
        FeedbackEmailService service =
                new FeedbackEmailService(emailSender, "endpoint=https://email.example.com/;accesskey=test",
                        "from@example.com", "admin@example.com");

        service.send("Assunto", "Conteúdo");

        verify(emailSender).send(
                org.mockito.ArgumentMatchers.eq("endpoint=https://email.example.com/;accesskey=test"),
                org.mockito.ArgumentMatchers.argThat(message ->
                        message.getSenderAddress().equals("from@example.com")
                                && message.getToRecipients().get(0).getAddress().equals("admin@example.com")
                                && message.getSubject().equals("Assunto")
                                && message.getBodyPlainText().equals("Conteúdo"))
        );
    }

    @Test
    void deveRecusarEnvioSemConfiguracaoDoAzureEmail() {
        FeedbackEmailService service =
                new FeedbackEmailService(emailSender, "", "from@example.com", "admin@example.com");

        assertThrows(IllegalStateException.class, () -> service.send("Assunto", "Conteúdo"));
    }
}

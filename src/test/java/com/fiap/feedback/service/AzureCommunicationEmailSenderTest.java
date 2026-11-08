package com.fiap.feedback.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.util.polling.SyncPoller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureCommunicationEmailSenderTest {

    private final AzureCommunicationEmailSender sender =
            new AzureCommunicationEmailSender();

    @Test
    void deveEnviarEmailComSucesso() {
        String connectionString = "connection-string-teste";
        EmailMessage message = mock(EmailMessage.class);

        EmailClient emailClient = mock(EmailClient.class);

        @SuppressWarnings("unchecked")
        SyncPoller<EmailSendResult, EmailSendResult> poller =
                mock(SyncPoller.class);

        when(emailClient.beginSend(message)).thenReturn(poller);

        try (MockedConstruction<EmailClientBuilder> builders =
                     mockConstruction(EmailClientBuilder.class, (builder, context) -> {
                         when(builder.connectionString(connectionString)).thenReturn(builder);
                         when(builder.buildClient()).thenReturn(emailClient);
                     })) {

            sender.send(connectionString, message);

            EmailClientBuilder builder = builders.constructed().get(0);

            verify(builder).connectionString(connectionString);
            verify(builder).buildClient();
            verify(emailClient).beginSend(message);
            verify(poller).waitForCompletion();
        }
    }

    @Test
    void devePropagarExcecaoQuandoEnvioDeEmailFalhar() {
        String connectionString = "connection-string-teste";
        EmailMessage message = mock(EmailMessage.class);

        EmailClient emailClient = mock(EmailClient.class);

        when(emailClient.beginSend(message))
                .thenThrow(new RuntimeException("Erro ao enviar e-mail"));

        try (MockedConstruction<EmailClientBuilder> builders =
                     mockConstruction(EmailClientBuilder.class, (builder, context) -> {
                         when(builder.connectionString(connectionString)).thenReturn(builder);
                         when(builder.buildClient()).thenReturn(emailClient);
                     })) {

            assertThrows(
                    RuntimeException.class,
                    () -> sender.send(connectionString, message)
            );
        }
    }
}


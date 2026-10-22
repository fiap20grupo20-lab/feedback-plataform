package com.fiap.feedback.service;

import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FeedbackEmailService {
    private final AzureCommunicationEmailSender emailSender;
    private final String connectionString;
    private final String from;
    private final String recipient;

    public FeedbackEmailService(
            AzureCommunicationEmailSender emailSender,
            @Value("${azure.communication.connection-string:}") String connectionString,
            @Value("${app.notifications.from:}") String from,
            @Value("${app.notifications.recipient:}") String recipient) {
        this.emailSender = emailSender;
        this.connectionString = connectionString;
        this.from = from;
        this.recipient = recipient;
    }

    public void send(String subject, String body) {
        if (connectionString.isBlank() || from.isBlank() || recipient.isBlank()) {
            throw new IllegalStateException(
                    "Configure AZURE_COMMUNICATION_CONNECTION_STRING, AZURE_EMAIL_SENDER e "
                            + "AZURE_NOTIFICATION_EMAIL para enviar e-mails.");
        }

        EmailMessage message = new EmailMessage()
                .setSenderAddress(from)
                .setToRecipients(new EmailAddress(recipient))
                .setSubject(subject)
                .setBodyPlainText(body);
        emailSender.send(connectionString, message);
    }
}

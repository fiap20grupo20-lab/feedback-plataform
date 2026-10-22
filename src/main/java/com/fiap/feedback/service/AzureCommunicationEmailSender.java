package com.fiap.feedback.service;

import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import org.springframework.stereotype.Component;

@Component
public class AzureCommunicationEmailSender {
    public void send(String connectionString, EmailMessage message) {
        new EmailClientBuilder()
                .connectionString(connectionString)
                .buildClient()
                .beginSend(message)
                .waitForCompletion();
    }
}

package com.fiap.feedback.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.feedback.FeedbackApplication;
import com.fiap.feedback.dto.FeedbackRequest;
import com.fiap.feedback.dto.WeeklyFeedbackReport;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.service.FeedbackService;
import com.fiap.feedback.service.UrgencyNotificationService;
import com.fiap.feedback.service.WeeklyFeedbackReportService;
import com.fiap.feedback.service.WeeklyFeedbackReportEmailService;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.QueueOutput;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

public class FeedbackFunction {

    private static final ConfigurableApplicationContext SPRING_CONTEXT =
            SpringApplication.run(FeedbackApplication.class);

    @FunctionName("avaliacao-post")
    public HttpResponseMessage criar(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.POST},
                    route = "avaliacao",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context,
            @QueueOutput(
                    name = "notification",
                    queueName = "urgency-notifications",
                    connection = "AzureWebJobsStorage")
            OutputBinding<String> notificationOutput) {
        try {
            String body = request.getBody().orElseThrow(() -> new IllegalArgumentException("Body obrigatório"));
            ObjectMapper mapper = SPRING_CONTEXT.getBean(ObjectMapper.class);
            FeedbackRequest payload = mapper.readValue(body, FeedbackRequest.class);

            Validator validator = SPRING_CONTEXT.getBean(Validator.class);
            Set<ConstraintViolation<FeedbackRequest>> violations = validator.validate(payload);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .findFirst()
                        .orElse("Dados inválidos");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", message))
                        .build();
            }

            FeedbackService service = SPRING_CONTEXT.getBean(FeedbackService.class);
            Feedback feedback = service.criar(payload);
            if (feedback.getUrgencia() == Urgencia.ALTA) {
                notificationOutput.setValue(mapper.writeValueAsString(feedback));
            }
            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(feedback)
                    .build();
        } catch (IllegalArgumentException ex) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()))
                    .build();
        } catch (Exception ex) {
            context.getLogger().log(Level.SEVERE, "Falha ao processar o feedback.", ex);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Falha ao processar o feedback: " + ex.getMessage()))
                    .build();
        }
    }

    @FunctionName("avaliacao-list")
    public HttpResponseMessage listar(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.GET},
                    route = "avaliacao",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        try {
            FeedbackService service = SPRING_CONTEXT.getBean(FeedbackService.class);
            List<Feedback> feedbacks = service.listar();
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(feedbacks)
                    .build();
        } catch (Exception ex) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Falha ao consultar feedbacks no Azure Cosmos DB: " + ex.getMessage()))
                    .build();
        }
    }

    @FunctionName("health")
    public HttpResponseMessage health(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.GET},
                    route = "health",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return request.createResponseBuilder(HttpStatus.OK)
                .body(Map.of("status", "UP"))
                .build();
    }

    @FunctionName("notificacao-urgencia")
    public void notificarUrgencia(
            @QueueTrigger(
                    name = "notification",
                    queueName = "urgency-notifications",
                    connection = "AzureWebJobsStorage")
            String message,
            final ExecutionContext context) {
        try {
            Feedback feedback = SPRING_CONTEXT.getBean(ObjectMapper.class).readValue(message, Feedback.class);
            SPRING_CONTEXT.getBean(UrgencyNotificationService.class).notifyIfUrgent(feedback);
            context.getLogger().info("Notificação de urgência enviada para o feedback " + feedback.getId());
        } catch (Exception exception) {
            context.getLogger().log(Level.SEVERE, "Falha ao enviar notificação de urgência.", exception);
            throw new IllegalStateException("Falha ao enviar notificação de urgência.", exception);
        }
    }

    @FunctionName("relatorio-semanal")
    public void gerarRelatorioSemanal(
            @TimerTrigger(name = "timerInfo", schedule = "0 0 9 * * MON") String timerInfo,
            final ExecutionContext context) {
        try {
            WeeklyFeedbackReportService reportService =
                    SPRING_CONTEXT.getBean(WeeklyFeedbackReportService.class);
            WeeklyFeedbackReport report = reportService.generatePreviousWeekReport();
            SPRING_CONTEXT.getBean(WeeklyFeedbackReportEmailService.class).send(report);
            context.getLogger().info(
                    "Relatório semanal enviado. Período: " + report.weekStart() + " a " + report.weekEnd()
                            + "; avaliações: " + report.totalFeedbacks()
                            + "; média: " + report.averageRating()
            );
        } catch (Exception exception) {
            context.getLogger().log(Level.SEVERE, "Falha ao gerar o relatório semanal de feedbacks.", exception);
            throw new IllegalStateException("Falha ao gerar o relatório semanal de feedbacks.", exception);
        }
    }
}

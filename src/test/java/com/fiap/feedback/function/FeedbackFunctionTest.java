package com.fiap.feedback.function;

import com.fiap.feedback.FeedbackApplication;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import com.fiap.feedback.service.FeedbackService;
import com.fiap.feedback.model.Feedback;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.feedback.dto.FeedbackRequest;
import com.microsoft.azure.functions.ExecutionContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import com.fiap.feedback.enums.Urgencia;
import com.microsoft.azure.functions.OutputBinding;
import java.util.Set;
import java.util.logging.Logger;
import static org.mockito.ArgumentMatchers.eq;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedbackFunctionTest {

    private ConfigurableApplicationContext springContext;
    private MockedStatic<SpringApplication> springApplication;
    private FeedbackFunction function;

    @Mock
    private HttpRequestMessage<Optional<String>> request;

    @Mock
    private HttpResponseMessage response;

    @Mock
    private HttpResponseMessage.Builder responseBuilder;

    @BeforeAll
    void setUp() {
        springContext = mock(ConfigurableApplicationContext.class);

        springApplication = mockStatic(SpringApplication.class);

        springApplication
                .when(() -> SpringApplication.run(FeedbackApplication.class))
                .thenReturn(springContext);

        function = new FeedbackFunction();
    }

    @AfterAll
    void tearDown() {
        springApplication.close();
    }

    @Test
    void deveRetornarStatusUpNoHealthCheck() {
        when(request.createResponseBuilder(HttpStatus.OK))
                .thenReturn(responseBuilder);
        when(responseBuilder.body(any()))
                .thenReturn(responseBuilder);
        when(responseBuilder.build())
                .thenReturn(response);

        HttpResponseMessage resultado = function.health(request, null);

        assertSame(response, resultado);
        verify(request).createResponseBuilder(HttpStatus.OK);
        verify(responseBuilder).body(Map.of("status", "UP"));
        verify(responseBuilder).build();
    }

    @Test
    void deveListarFeedbacksComSucesso() {
        FeedbackService service = mock(FeedbackService.class);
        List<Feedback> feedbacks = List.of();

        when(springContext.getBean(FeedbackService.class))
                .thenReturn(service);

        when(service.listar())
                .thenReturn(feedbacks);

        when(request.createResponseBuilder(HttpStatus.OK))
                .thenReturn(responseBuilder);

        when(responseBuilder.header("Content-Type", "application/json"))
                .thenReturn(responseBuilder);

        when(responseBuilder.body(feedbacks))
                .thenReturn(responseBuilder);

        when(responseBuilder.build())
                .thenReturn(response);

        HttpResponseMessage resultado = function.listar(request, null);

        assertSame(response, resultado);

        verify(springContext).getBean(FeedbackService.class);
        verify(service).listar();
        verify(request).createResponseBuilder(HttpStatus.OK);
        verify(responseBuilder).header("Content-Type", "application/json");
        verify(responseBuilder).body(feedbacks);
        verify(responseBuilder).build();
    }

    @Test
    void deveRetornarErro500QuandoFalharAoListarFeedbacks() {
        FeedbackService service = mock(FeedbackService.class);
        String mensagemErro = "Erro de conexão com o banco";

        when(springContext.getBean(FeedbackService.class))
                .thenReturn(service);

        when(service.listar())
                .thenThrow(new RuntimeException(mensagemErro));

        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(responseBuilder);

        when(responseBuilder.body(Map.of(
                "error",
                "Falha ao consultar feedbacks no Azure Cosmos DB: " + mensagemErro
        ))).thenReturn(responseBuilder);

        when(responseBuilder.build())
                .thenReturn(response);

        clearInvocations(springContext);
        HttpResponseMessage resultado = function.listar(request, null);

        assertSame(response, resultado);

        verify(springContext).getBean(FeedbackService.class);
        verify(service).listar();
        verify(request).createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(responseBuilder).body(Map.of(
                "error",
                "Falha ao consultar feedbacks no Azure Cosmos DB: " + mensagemErro
        ));
        verify(responseBuilder).build();
    }

    @Test
    void deveRetornarBadRequestQuandoBodyEstiverVazio() {
        when(request.getBody())
                .thenReturn(Optional.empty());

        when(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
                .thenReturn(responseBuilder);

        when(responseBuilder.body(Map.of("error", "Body obrigatório")))
                .thenReturn(responseBuilder);

        when(responseBuilder.build())
                .thenReturn(response);

        clearInvocations(springContext);

        HttpResponseMessage resultado = function.criar(request, null, null);

        assertSame(response, resultado);

        verify(request).getBody();
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verify(responseBuilder).body(Map.of("error", "Body obrigatório"));
        verify(responseBuilder).build();

        verifyNoInteractions(springContext);
    }

    @Test
    void deveRetornarErro500QuandoJsonForInvalido() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        ExecutionContext context = mock(ExecutionContext.class);
        Logger logger = mock(Logger.class);

        String body = "{json-invalido";
        String mensagemErro = "JSON inválido";

        when(request.getBody())
                .thenReturn(Optional.of(body));

        when(springContext.getBean(ObjectMapper.class))
                .thenReturn(mapper);

        when(context.getLogger())
                .thenReturn(logger);

        when(mapper.readValue(body, FeedbackRequest.class))
                .thenThrow(new RuntimeException(mensagemErro));

        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(responseBuilder);

        when(responseBuilder.body(Map.of(
                "error",
                "Falha ao processar o feedback: " + mensagemErro
        ))).thenReturn(responseBuilder);

        when(responseBuilder.build())
                .thenReturn(response);

        clearInvocations(springContext);

        HttpResponseMessage resultado = function.criar(request, context, null);

        assertSame(response, resultado);

        verify(springContext).getBean(ObjectMapper.class);
        verify(mapper).readValue(body, FeedbackRequest.class);
        verify(request).createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(responseBuilder).body(Map.of(
                "error",
                "Falha ao processar o feedback: " + mensagemErro
        ));
        verify(responseBuilder).build();
    }

    @Test
    void deveRetornarBadRequestQuandoDadosForemInvalidos() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        Validator validator = mock(Validator.class);

        FeedbackRequest payload = new FeedbackRequest("", 8, null);

        ConstraintViolation<FeedbackRequest> violation =
                mock(ConstraintViolation.class);

        String body = "{\"descricao\":\"\",\"nota\":8,\"urgencia\":null}";
        String mensagemErro = "Descrição é obrigatória";

        when(request.getBody())
                .thenReturn(Optional.of(body));

        when(springContext.getBean(ObjectMapper.class))
                .thenReturn(mapper);

        when(mapper.readValue(body, FeedbackRequest.class))
                .thenReturn(payload);

        when(springContext.getBean(Validator.class))
                .thenReturn(validator);

        when(validator.validate(payload))
                .thenReturn(Set.of(violation));

        when(violation.getMessage())
                .thenReturn(mensagemErro);

        when(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
                .thenReturn(responseBuilder);

        when(responseBuilder.body(Map.of("error", mensagemErro)))
                .thenReturn(responseBuilder);

        when(responseBuilder.build())
                .thenReturn(response);

        clearInvocations(springContext);

        HttpResponseMessage resultado = function.criar(request, null, null);

        assertSame(response, resultado);

        verify(springContext).getBean(ObjectMapper.class);
        verify(springContext).getBean(Validator.class);
        verify(mapper).readValue(body, FeedbackRequest.class);
        verify(validator).validate(payload);
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verify(responseBuilder).body(Map.of("error", mensagemErro));
        verify(responseBuilder).build();
    }

    @Test
    void deveCriarFeedbackComSucesso() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        Validator validator = mock(Validator.class);
        FeedbackService service = mock(FeedbackService.class);
        OutputBinding<String> notificationOutput = mock(OutputBinding.class);

        String body = "{\"descricao\":\"Atendimento satisfatório\",\"nota\":8,\"urgencia\":\"MEDIA\"}";

        FeedbackRequest payload = new FeedbackRequest(
                "Atendimento satisfatório",
                8,
                Urgencia.MEDIA
        );

        Feedback feedback = new Feedback();
        feedback.setId("123");
        feedback.setDescricao("Atendimento satisfatório");
        feedback.setNota(8);
        feedback.setUrgencia(Urgencia.MEDIA);
        feedback.setDataEnvio("2026-11-08");

        when(request.getBody())
                .thenReturn(Optional.of(body));

        when(springContext.getBean(ObjectMapper.class))
                .thenReturn(mapper);

        when(mapper.readValue(body, FeedbackRequest.class))
                .thenReturn(payload);

        when(springContext.getBean(Validator.class))
                .thenReturn(validator);

        when(validator.validate(payload))
                .thenReturn(Set.of());

        when(springContext.getBean(FeedbackService.class))
                .thenReturn(service);

        when(service.criar(payload))
                .thenReturn(feedback);

        when(request.createResponseBuilder(HttpStatus.CREATED))
                .thenReturn(responseBuilder);

        when(responseBuilder.header("Content-Type", "application/json"))
                .thenReturn(responseBuilder);

        when(responseBuilder.body(feedback))
                .thenReturn(responseBuilder);

        when(responseBuilder.build())
                .thenReturn(response);

        clearInvocations(springContext);

        HttpResponseMessage resultado = function.criar(
                request,
                null,
                notificationOutput
        );

        assertSame(response, resultado);

        verify(springContext).getBean(ObjectMapper.class);
        verify(springContext).getBean(Validator.class);
        verify(springContext).getBean(FeedbackService.class);

        verify(mapper).readValue(body, FeedbackRequest.class);
        verify(validator).validate(payload);
        verify(service).criar(payload);

        verify(request).createResponseBuilder(HttpStatus.CREATED);
        verify(responseBuilder).header("Content-Type", "application/json");
        verify(responseBuilder).body(feedback);
        verify(responseBuilder).build();

        verifyNoInteractions(notificationOutput);
    }

    @Test
    void deveCriarFeedbackEEnviarNotificacaoQuandoUrgenciaForAlta() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        Validator validator = mock(Validator.class);
        FeedbackService service = mock(FeedbackService.class);
        OutputBinding<String> notificationOutput = mock(OutputBinding.class);

        String body = "{\"descricao\":\"Atendimento crítico\",\"nota\":2,\"urgencia\":\"ALTA\"}";
        String feedbackJson = "{\"id\":\"123\",\"descricao\":\"Atendimento crítico\",\"nota\":2,\"urgencia\":\"ALTA\"}";

        FeedbackRequest payload = new FeedbackRequest(
                "Atendimento crítico",
                2,
                Urgencia.ALTA
        );

        Feedback feedback = new Feedback();
        feedback.setId("123");
        feedback.setDescricao("Atendimento crítico");
        feedback.setNota(2);
        feedback.setUrgencia(Urgencia.ALTA);
        feedback.setDataEnvio("2026-11-08");

        when(request.getBody())
                .thenReturn(Optional.of(body));

        when(springContext.getBean(ObjectMapper.class))
                .thenReturn(mapper);

        when(mapper.readValue(body, FeedbackRequest.class))
                .thenReturn(payload);

        when(springContext.getBean(Validator.class))
                .thenReturn(validator);

        when(validator.validate(payload))
                .thenReturn(Set.of());

        when(springContext.getBean(FeedbackService.class))
                .thenReturn(service);

        when(service.criar(payload))
                .thenReturn(feedback);

        when(mapper.writeValueAsString(feedback))
                .thenReturn(feedbackJson);

        when(request.createResponseBuilder(HttpStatus.CREATED))
                .thenReturn(responseBuilder);

        when(responseBuilder.header("Content-Type", "application/json"))
                .thenReturn(responseBuilder);

        when(responseBuilder.body(feedback))
                .thenReturn(responseBuilder);

        when(responseBuilder.build())
                .thenReturn(response);

        clearInvocations(springContext);

        HttpResponseMessage resultado = function.criar(
                request,
                null,
                notificationOutput
        );

        assertSame(response, resultado);

        verify(mapper).writeValueAsString(feedback);
        verify(notificationOutput).setValue(feedbackJson);

        verify(service).criar(payload);
        verify(validator).validate(payload);

        verify(request).createResponseBuilder(HttpStatus.CREATED);
        verify(responseBuilder).body(feedback);
        verify(responseBuilder).build();
    }

    @Test
    void deveRetornarErro500QuandoFalharAoCriarFeedback() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        Validator validator = mock(Validator.class);
        FeedbackService service = mock(FeedbackService.class);
        ExecutionContext context = mock(ExecutionContext.class);
        Logger logger = mock(Logger.class);

        String body = "{\"descricao\":\"Atendimento\",\"nota\":8,\"urgencia\":\"MEDIA\"}";
        String mensagemErro = "Erro ao salvar feedback";

        FeedbackRequest payload = new FeedbackRequest(
                "Atendimento",
                8,
                Urgencia.MEDIA
        );

        when(request.getBody())
                .thenReturn(Optional.of(body));

        when(springContext.getBean(ObjectMapper.class))
                .thenReturn(mapper);

        when(mapper.readValue(body, FeedbackRequest.class))
                .thenReturn(payload);

        when(springContext.getBean(Validator.class))
                .thenReturn(validator);

        when(validator.validate(payload))
                .thenReturn(Set.of());

        when(springContext.getBean(FeedbackService.class))
                .thenReturn(service);

        when(service.criar(payload))
                .thenThrow(new RuntimeException(mensagemErro));

        when(context.getLogger())
                .thenReturn(logger);

        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(responseBuilder);

        when(responseBuilder.body(Map.of(
                "error",
                "Falha ao processar o feedback: " + mensagemErro
        ))).thenReturn(responseBuilder);

        when(responseBuilder.build())
                .thenReturn(response);

        clearInvocations(springContext);

        HttpResponseMessage resultado = function.criar(request, context, null);

        assertSame(response, resultado);

        verify(service).criar(payload);

        verify(context).getLogger();

        verify(logger).log(
                eq(java.util.logging.Level.SEVERE),
                eq("Falha ao processar o feedback."),
                any(Exception.class)
        );

        verify(request).createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR);

        verify(responseBuilder).body(Map.of(
                "error",
                "Falha ao processar o feedback: " + mensagemErro
        ));

        verify(responseBuilder).build();
    }

}
package com.fiap.feedback.repository;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedIterable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemResponse;
import com.fiap.feedback.model.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class FeedbackRepositoryTest {

    private final CosmosClient cosmosClient = mock(CosmosClient.class);
    private final CosmosDatabase database = mock(CosmosDatabase.class);
    private final CosmosContainer container = mock(CosmosContainer.class);
    private FeedbackRepository repository;

    @BeforeEach
    void setUp() {
        when(cosmosClient.getDatabase("feedback-db")).thenReturn(database);
        when(database.getContainer("feedback-container")).thenReturn(container);
        repository = new FeedbackRepository(cosmosClient, "feedback-db", "feedback-container");
    }

    @Test
    void deveRetornarFeedbackEnviadoQuandoCosmosNaoRetornarItemNaResposta() {
        Feedback feedback = new Feedback();
        feedback.setId("feedback-123");
        feedback.setDescricao("Atendimento bom");

        @SuppressWarnings("unchecked")
        CosmosItemResponse<Feedback> response = mock(CosmosItemResponse.class);
        when(container.createItem(feedback)).thenReturn(response);
        when(response.getItem()).thenReturn(null);

        assertSame(feedback, repository.save(feedback));
    }

    @Test
    void deveFalharExplicitamenteQuandoCosmosNaoRetornarResposta() {
        Feedback feedback = new Feedback();
        when(container.createItem(feedback)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> repository.save(feedback));
    }

    @Test
    void deveRetornarFeedbackSalvoPeloCosmos() {
        Feedback feedback = new Feedback();
        feedback.setId("feedback-123");

        Feedback feedbackSalvo = new Feedback();
        feedbackSalvo.setId("feedback-456");

        @SuppressWarnings("unchecked")
        CosmosItemResponse<Feedback> response = mock(CosmosItemResponse.class);

        when(container.createItem(feedback)).thenReturn(response);
        when(response.getItem()).thenReturn(feedbackSalvo);

        Feedback resultado = repository.save(feedback);

        assertSame(feedbackSalvo, resultado);
    }

    @Test
    void deveRetornarTodosOsFeedbacksDeTodasAsPaginas() {
        Feedback feedback1 = new Feedback();
        feedback1.setId("feedback-1");

        Feedback feedback2 = new Feedback();
        feedback2.setId("feedback-2");

        @SuppressWarnings("unchecked")
        CosmosPagedIterable<Feedback> items = mock(CosmosPagedIterable.class);

        @SuppressWarnings("unchecked")
        FeedResponse<Feedback> page1 = mock(FeedResponse.class);

        @SuppressWarnings("unchecked")
        FeedResponse<Feedback> page2 = mock(FeedResponse.class);

        when(container.queryItems(
                eq("SELECT * FROM c"),
                any(CosmosQueryRequestOptions.class),
                eq(Feedback.class)
        )).thenReturn(items);

        when(items.iterableByPage()).thenReturn(List.of(page1, page2));
        when(page1.getResults()).thenReturn(List.of(feedback1));
        when(page2.getResults()).thenReturn(List.of(feedback2));

        List<Feedback> resultado = repository.findAll();

        assertEquals(2, resultado.size());
        assertSame(feedback1, resultado.get(0));
        assertSame(feedback2, resultado.get(1));
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistiremFeedbacks() {
        @SuppressWarnings("unchecked")
        CosmosPagedIterable<Feedback> items = mock(CosmosPagedIterable.class);

        when(container.queryItems(
                eq("SELECT * FROM c"),
                any(CosmosQueryRequestOptions.class),
                eq(Feedback.class)
        )).thenReturn(items);

        when(items.iterableByPage()).thenReturn(List.of());

        List<Feedback> resultado = repository.findAll();

        assertTrue(resultado.isEmpty());
    }
}

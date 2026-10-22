package com.fiap.feedback.repository;

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
}

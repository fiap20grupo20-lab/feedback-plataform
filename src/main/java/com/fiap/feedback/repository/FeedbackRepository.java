package com.fiap.feedback.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fiap.feedback.model.Feedback;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class FeedbackRepository {
    private final CosmosContainer container;

    public FeedbackRepository(CosmosClient cosmosClient,
                             @org.springframework.beans.factory.annotation.Value("${azure.cosmos.database-name}") String databaseName,
                             @org.springframework.beans.factory.annotation.Value("${azure.cosmos.container-name}") String containerName) {
        CosmosDatabase database = cosmosClient.getDatabase(databaseName);
        this.container = database.getContainer(containerName);
    }

    public Feedback save(Feedback feedback) {
        CosmosItemResponse<Feedback> response = container.createItem(feedback);
        if (response == null) {
            throw new IllegalStateException("Azure Cosmos DB não retornou uma resposta ao salvar o feedback.");
        }

        Feedback savedFeedback = response.getItem();
        return savedFeedback != null ? savedFeedback : feedback;
    }

    public List<Feedback> findAll() {
        String query = "SELECT * FROM c";
        CosmosPagedIterable<Feedback> items = container.queryItems(query, new CosmosQueryRequestOptions(), Feedback.class);
        List<Feedback> feedbacks = new ArrayList<>();
        for (FeedResponse<Feedback> page : items.iterableByPage()) {
            feedbacks.addAll(page.getResults());
        }
        return feedbacks;
    }
}
package com.fiap.feedback.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosConfig {

    @Bean
    public CosmosClient cosmosClient(
            @Value("${azure.cosmos.endpoint}") String endpoint,
            @Value("${azure.cosmos.key:}") String key) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException(
                    "Azure Cosmos DB não configurado. Defina AZURE_COSMOS_ENDPOINT.");
        }

        if (key != null && !key.isBlank()) {
            return new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .key(key)
                    .buildClient();
        }

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}

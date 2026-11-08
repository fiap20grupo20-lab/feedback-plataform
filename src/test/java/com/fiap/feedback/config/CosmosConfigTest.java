
package com.fiap.feedback.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CosmosConfigTest {

    @Mock
    private CosmosClient cosmosClient;

    @InjectMocks
    private CosmosConfig cosmosConfig;

    @Test
    void deveLancarExcecaoQuandoEndpointForNulo() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> cosmosConfig.cosmosClient(null, "chave-teste")
        );

        assertEquals(
                "Azure Cosmos DB não configurado. Defina AZURE_COSMOS_ENDPOINT.",
                exception.getMessage()
        );
    }

    @Test
    void deveLancarExcecaoQuandoEndpointEstiverVazio() {
        assertThrows(
                IllegalStateException.class,
                () -> cosmosConfig.cosmosClient(" ", "chave-teste")
        );
    }

    @Test
    void deveCriarCosmosClientComChaveDeAcesso() {
        String endpoint = "https://localhost:8081";
        String key = "chave-teste";

        try (MockedConstruction<CosmosClientBuilder> builders =
                     mockConstruction(CosmosClientBuilder.class, (builder, context) -> {
                         when(builder.endpoint(anyString())).thenReturn(builder);
                         when(builder.key(anyString())).thenReturn(builder);
                         when(builder.credential(any(TokenCredential.class))).thenReturn(builder);
                         when(builder.buildClient()).thenReturn(cosmosClient);
                     })) {

            CosmosClient resultado = cosmosConfig.cosmosClient(endpoint, key);

            assertSame(cosmosClient, resultado);

            CosmosClientBuilder builder = builders.constructed().get(0);

            verify(builder).endpoint(endpoint);
            verify(builder).key(key);
            verify(builder, never()).credential(any(TokenCredential.class));
            verify(builder).buildClient();
        }
    }

    @Test
    void deveCriarCosmosClientComCredencialPadraoQuandoChaveForVazia() {
        testarCriacaoComCredencialPadrao("");
    }

    @Test
    void deveCriarCosmosClientComCredencialPadraoQuandoChaveForNula() {
        testarCriacaoComCredencialPadrao(null);
    }

    private void testarCriacaoComCredencialPadrao(String key) {
        String endpoint = "https://localhost:8081";

        DefaultAzureCredential credential = mock(DefaultAzureCredential.class);

        try (MockedConstruction<DefaultAzureCredentialBuilder> credentials =
                     mockConstruction(DefaultAzureCredentialBuilder.class,
                             (builder, context) ->
                                     when(builder.build()).thenReturn(credential));

             MockedConstruction<CosmosClientBuilder> builders =
                     mockConstruction(CosmosClientBuilder.class, (builder, context) -> {
                         when(builder.endpoint(anyString())).thenReturn(builder);
                         when(builder.credential(any(TokenCredential.class))).thenReturn(builder);
                         when(builder.buildClient()).thenReturn(cosmosClient);
                     })) {

            CosmosClient resultado = cosmosConfig.cosmosClient(endpoint, key);

            assertSame(cosmosClient, resultado);

            DefaultAzureCredentialBuilder credentialBuilder =
                    credentials.constructed().get(0);

            CosmosClientBuilder cosmosBuilder = builders.constructed().get(0);

            verify(credentialBuilder).build();
            verify(cosmosBuilder).endpoint(endpoint);
            verify(cosmosBuilder).credential(credential);
            verify(cosmosBuilder).buildClient();
        }
    }
}

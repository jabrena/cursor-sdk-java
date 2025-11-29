package info.jab.cursor.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DefaultTokenProvider using WireMock to mock the authentication API.
 */
class DefaultTokenProviderIntegrationTest {

    private WireMockServer wireMockServer;
    private TokenProvider tokenProvider;
    private String authEndpoint;

    @BeforeEach
    void setUp() {
        // Start WireMock server on a random port
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // Create the auth endpoint URL using the WireMock server port
        authEndpoint = "http://localhost:" + wireMockServer.port() + "/auth/exchange_user_api_key";

        // Create the token provider with the WireMock endpoint
        tokenProvider = new DefaultTokenProvider(authEndpoint);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void testGetToken_Success() {
        // Given
        stubFor(post(urlEqualTo("/auth/exchange_user_api_key"))
                .withHeader("Authorization", WireMock.matching("Bearer .+"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("200-auth-success.json")));

        // When
        String apiKey = "test-api-key-123";
        String accessToken = tokenProvider.getToken(apiKey);

        // Then
        assertNotNull(accessToken);
        assertFalse(accessToken.isEmpty());

        verify(postRequestedFor(urlEqualTo("/auth/exchange_user_api_key"))
                .withHeader("Authorization", equalTo("Bearer " + apiKey))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{}")));
    }

    @Test
    void testGetToken_Unauthorized() {
        // Given
        stubFor(post(urlEqualTo("/auth/exchange_user_api_key"))
                .withHeader("Authorization", WireMock.matching("Bearer .+"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{}"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("401-auth-bad-credentials.json")));

        // When & Then
        String apiKey = "invalid-api-key";
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tokenProvider.getToken(apiKey);
        });

        assertTrue(exception.getMessage().contains("Failed to exchange API key"));
        assertTrue(exception.getMessage().contains("401"));
        assertTrue(exception.getMessage().contains("code") || exception.getMessage().contains("message"));

        verify(postRequestedFor(urlEqualTo("/auth/exchange_user_api_key"))
                .withHeader("Authorization", equalTo("Bearer " + apiKey))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{}")));
    }

    @Test
    void testGetToken_InvalidApiKey() {
        // Test that null API key throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            tokenProvider.getToken(null);
        });

        // Test that empty API key throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            tokenProvider.getToken("");
        });
    }

    @Test
    void testGetToken_MissingAccessTokenInResponse() {
        // Given
        String invalidResponse = "{\"refreshToken\": \"some-token\"}";

        stubFor(post(urlEqualTo("/auth/exchange_user_api_key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(invalidResponse)));

        // When & Then
        String apiKey = "test-api-key";
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tokenProvider.getToken(apiKey);
        });

        assertTrue(exception.getMessage().contains("Access token not found in response"));
    }

    @Test
    void testTokenProviderInterface() {
        // Verify that DefaultTokenProvider implements TokenProvider interface
        assertTrue(tokenProvider instanceof TokenProvider);
        assertTrue(tokenProvider instanceof DefaultTokenProvider);
    }
}


package info.jab.cursor.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Default implementation of TokenProvider that exchanges a Cursor API key
 * for an access token by calling the Cursor authentication API.
 */
public class DefaultTokenProvider implements TokenProvider {

    private static final String DEFAULT_AUTH_ENDPOINT = "https://api2.cursor.sh/auth/exchange_user_api_key";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String authEndpoint;

    /**
     * Creates a new DefaultTokenProvider with a default HttpClient and default auth endpoint.
     */
    public DefaultTokenProvider() {
        this.httpClient = HttpClient.newHttpClient();
        this.authEndpoint = DEFAULT_AUTH_ENDPOINT;
    }

    /**
     * Creates a new DefaultTokenProvider with a custom auth endpoint.
     * This constructor is useful for testing with WireMock or other mock servers.
     *
     * @param authEndpoint the authentication endpoint URL
     */
    public DefaultTokenProvider(String authEndpoint) {
        if (authEndpoint == null || authEndpoint.isEmpty()) {
            throw new IllegalArgumentException("Auth endpoint cannot be null or empty");
        }
        this.httpClient = HttpClient.newHttpClient();
        this.authEndpoint = authEndpoint;
    }

    /**
     * Exchanges a Cursor API key for an access token.
     *
     * @param cursorApiKey the Cursor API key to exchange
     * @return the access token
     * @throws RuntimeException if the API key exchange fails or the response cannot be parsed
     */
    @Override
    public String getToken(String cursorApiKey) {
        if (cursorApiKey == null || cursorApiKey.isEmpty()) {
            throw new IllegalArgumentException("Cursor API key cannot be null or empty");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authEndpoint))
                    .header("Authorization", "Bearer " + cursorApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to exchange API key. Status: " + response.statusCode() + ", Body: " + response.body());
            }

            JsonNode rootNode = OBJECT_MAPPER.readTree(response.body());
            JsonNode accessTokenNode = rootNode.get("accessToken");

            if (accessTokenNode == null || accessTokenNode.isNull()) {
                throw new RuntimeException("Access token not found in response: " + response.body());
            }

            return accessTokenNode.asText();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to obtain access token: " + e.getMessage(), e);
        }
    }
}


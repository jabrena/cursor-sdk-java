package info.jab.cursor.sdk;

/**
 * Response from the Cursor agent containing the agent's response text.
 */
public class AgentResponse {
    private final String response;

    /**
     * Creates a new AgentResponse with the given response text.
     *
     * @param response the response text from the agent
     */
    public AgentResponse(String response) {
        this.response = response;
    }

    /**
     * Gets the response text from the agent.
     *
     * @return the response text
     */
    public String getResponse() {
        return response;
    }
}


package info.jab.cursor.sdk;

/**
 * Interface for interacting with the Cursor agent service.
 */
public interface CursorAgent {
    /**
     * Submits a user prompt to the agent and returns the response.
     * This is a blocking call that waits for the agent to complete processing.
     *
     * @param userPrompt the user's prompt/question
     * @return the agent's response
     */
    AgentResponse submit(String userPrompt);

    /**
     * Creates a new builder for constructing a CursorAgent instance.
     *
     * @return a new CursorAgentBuilder
     */
    static CursorAgentBuilder builder() {
        return new CursorAgentBuilder();
    }
}


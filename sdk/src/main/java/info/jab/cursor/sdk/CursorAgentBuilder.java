package info.jab.cursor.sdk;

import java.nio.file.Path;

/**
 * Builder for constructing CursorAgent instances.
 */
public class CursorAgentBuilder {
    private String model = "default";
    private RepositoryLocation repositoryLocation;
    private Path workingPath;
    private String accessToken;
    private int timeoutSeconds = 60;

    /**
     * Sets the model to use for the agent.
     *
     * @param model the model name
     * @return this builder
     */
    public CursorAgentBuilder model(String model) {
        this.model = model;
        return this;
    }

    /**
     * Sets the access token to use for authentication.
     *
     * @param accessToken the access token
     * @return this builder
     */
    public CursorAgentBuilder accessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    /**
     * Sets the working location for the agent.
     *
     * @param location the repository location type
     * @param path the working path as a Path object
     * @return this builder
     */
    public CursorAgentBuilder workingLocation(RepositoryLocation location, Path path) {
        this.repositoryLocation = location;
        this.workingPath = path;
        return this;
    }

    /**
     * Sets the timeout in seconds for waiting for agent response.
     *
     * @param timeoutSeconds the timeout in seconds
     * @return this builder
     */
    public CursorAgentBuilder timeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    /**
     * Builds a new CursorAgent instance with the configured settings.
     *
     * @return a new CursorAgent instance
     */
    public CursorAgent build() {
        if (repositoryLocation == null) {
            throw new IllegalStateException("Repository location must be set");
        }
        if (workingPath == null) {
            throw new IllegalStateException("Working path must be set");
        }
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalStateException("Access token must be set");
        }
        return new DefaultCursorAgent(model, repositoryLocation, workingPath, accessToken, timeoutSeconds);
    }
}


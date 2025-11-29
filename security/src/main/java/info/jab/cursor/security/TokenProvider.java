package info.jab.cursor.security;

public interface TokenProvider {
    String getToken(String cursorApiKey);

    /**
     * Creates a new TokenProvider with default settings.
     * @return a new DefaultTokenProvider instance
     */
    static TokenProvider create() {
        return new DefaultTokenProvider();
    }
}

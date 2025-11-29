package info.jab.cursor;

import info.jab.cursor.sdk.AgentResponse;
import info.jab.cursor.sdk.CursorAgent;
import info.jab.cursor.sdk.RepositoryLocation;
import info.jab.cursor.security.TokenProvider;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CursorAgentExample {

    public static void main(String[] args) {

        //Security
        String cursorApiKey = System.getenv("CURSOR_API_KEY");
        TokenProvider tokenProvider = TokenProvider.create();
        String accessToken = tokenProvider.getToken(cursorApiKey);

        //Agent interaction
        String userPrompt = "Can you say the days of the week?";
        CursorAgent cursorAgent = CursorAgent.builder()
            .model("default")
            .accessToken(accessToken)
            .workingLocation(RepositoryLocation.LOCAL,  Paths.get("sample"))
            .timeoutSeconds(20)
            .build();

        AgentResponse response = cursorAgent.submit(userPrompt); //Blocking call

        System.out.println(response.getResponse());
    }
}

package info.jab.cursor.sdk;

import agent.v1.Agent;
import agent.v1.AgentServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of CursorAgent that uses gRPC to communicate with the Cursor agent service.
 */
class DefaultCursorAgent implements CursorAgent {
    private static final String DEFAULT_API_ENDPOINT = "api2.cursor.sh";
    private static final int DEFAULT_API_PORT = 443;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final String model;
    private final RepositoryLocation repositoryLocation;
    private final Path workingPath;
    private final String accessToken;
    private final int timeoutSeconds;

    /**
     * Creates a new DefaultCursorAgent with the specified configuration.
     *
     * @param model the model name to use
     * @param repositoryLocation the repository location type
     * @param workingPath the working path
     * @param accessToken the access token (required)
     * @param timeoutSeconds the timeout in seconds for waiting for agent response
     */
    DefaultCursorAgent(String model, RepositoryLocation repositoryLocation, Path workingPath, String accessToken, int timeoutSeconds) {
        this.model = model;
        this.repositoryLocation = repositoryLocation;
        this.workingPath = workingPath;
        this.accessToken = accessToken;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public AgentResponse submit(String userPrompt) {
        System.out.println("DEBUG: Connecting to " + DEFAULT_API_ENDPOINT + ":" + DEFAULT_API_PORT);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(DEFAULT_API_ENDPOINT, DEFAULT_API_PORT)
                .useTransportSecurity()
                .build();

        try {
            Metadata metadata = createMetadata(accessToken);
            Channel interceptedChannel = ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata));

            CountDownLatch finishLatch = new CountDownLatch(1);
            AtomicReference<String> responseText = new AtomicReference<>("");
            AtomicReference<StreamObserver<Agent.AgentClientMessage>> requestStreamRef = new AtomicReference<>();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            StreamObserver<Agent.AgentServerMessage> responseObserver = new StreamObserver<Agent.AgentServerMessage>() {
                @Override
                public void onNext(Agent.AgentServerMessage value) {
                    System.out.println("DEBUG: Received message from server");
                    if (value.hasInteractionUpdate()) {
                        Agent.InteractionUpdate interaction = value.getInteractionUpdate();
                        if (interaction.hasTextDelta()) {
                            String delta = interaction.getTextDelta().getText();
                            System.out.println("DEBUG: Received text delta: " + delta.length() + " characters");
                            responseText.updateAndGet(current -> current + delta);
                        }
                    } else if (value.hasExecServerMessage()) {
                        System.out.println("DEBUG: Received ExecServerMessage from server");
                        // Handle exec server messages - provide request context
                        Agent.ExecServerMessage serverMsg = value.getExecServerMessage();
                        System.out.println("DEBUG: Server reply - ExecServerMessage details:");
                        System.out.println("DEBUG:   - Message ID: " + (serverMsg.getId().isEmpty() ? "not set" : serverMsg.getId()));
                        System.out.println("DEBUG:   - Has request_context_args: " + serverMsg.hasRequestContextArgs());
                        if (serverMsg.hasRequestContextArgs()) {
                            System.out.println("DEBUG:   - Server is asking for request context");
                        }
                        System.out.println("DEBUG: Preparing ExecClientMessage (second message) response with request context...");
                        Agent.AgentClientMessage reply = createExecClientMessage(serverMsg);

                        // Decode and display the ExecClientMessage contents
                        if (reply.hasExecClientMessage()) {
                            Agent.ExecClientMessage execMsg = reply.getExecClientMessage();
                            System.out.println("DEBUG: ExecClientMessage contents:");
                            if (execMsg.hasRequestContextResult()) {
                                Agent.RequestContextResult result = execMsg.getRequestContextResult();
                                if (result.hasSuccess()) {
                                    Agent.RequestContext ctx = result.getSuccess().getRequestContext();
                                    System.out.println("DEBUG:   - RequestContext:");
                                    System.out.println("DEBUG:     * Workspace Path: " + ctx.getWorkspacePath());
                                    if (ctx.hasEnv()) {
                                        Agent.RequestContextEnv env = ctx.getEnv();
                                        System.out.println("DEBUG:     * OS Version: " + env.getOsVersion());
                                        System.out.println("DEBUG:     * Shell: " + env.getShell());
                                        System.out.println("DEBUG:     * Workspace Paths: " + env.getWorkspacePathsList());
                                    }
                                    System.out.println("DEBUG:     * Shared Notes: " + ctx.getSharedNotesListing());
                                    System.out.println("DEBUG:     * Project Layouts: " + ctx.getProjectLayoutsCount() + " layout(s)");
                                }
                            }
                        }

                        StreamObserver<Agent.AgentClientMessage> observer = requestStreamRef.get();
                        if (observer != null) {
                            System.out.println("DEBUG: Sending ExecClientMessage (second message) with request context");
                            System.err.println("DEBUG: Sending ExecClientMessage bytes: " + HexFormat.of().formatHex(reply.toByteArray()));
                            observer.onNext(reply);

                            // Send stream close control message
                            Agent.ExecClientControlMessage controlMsg = Agent.ExecClientControlMessage.newBuilder()
                                    .setStreamClose(Agent.ExecClientStreamClose.newBuilder().build())
                                    .build();
                            Agent.AgentClientMessage controlReply = Agent.AgentClientMessage.newBuilder()
                                    .setExecClientControlMessage(controlMsg)
                                    .build();
                            System.err.println("DEBUG: Sending ExecClientControlMessage bytes: " + HexFormat.of().formatHex(controlReply.toByteArray()));
                            observer.onNext(controlReply);
                        } else {
                            System.err.println("DEBUG: ERROR - Request stream observer is null!");
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("DEBUG: Error in response observer: " + t.getMessage());
                    t.printStackTrace();
                    errorRef.set(t);
                    finishLatch.countDown();
                }

                @Override
                public void onCompleted() {
                    System.out.println("DEBUG: Response stream completed");
                    finishLatch.countDown();
                }
            };

            StreamObserver<Agent.AgentClientMessage> requestObserver = ClientCalls.asyncBidiStreamingCall(
                    interceptedChannel.newCall(AgentServiceGrpc.getRunMethod(), io.grpc.CallOptions.DEFAULT),
                    responseObserver);
            requestStreamRef.set(requestObserver);

            System.out.println("DEBUG: Sending RunRequest with prompt: " + userPrompt);
            Agent.AgentClientMessage runMsg = createRunRequest(userPrompt);
            System.err.println("DEBUG: Sending RunRequest bytes: " + HexFormat.of().formatHex(runMsg.toByteArray()));
            requestObserver.onNext(runMsg);

            System.out.println("DEBUG: Waiting for response (timeout: " + timeoutSeconds + " seconds)...");
            if (!finishLatch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                System.err.println("DEBUG: Timeout waiting for agent response");
                throw new RuntimeException("Timeout waiting for agent response");
            }

            if (errorRef.get() != null) {
                System.err.println("DEBUG: Error received from agent: " + errorRef.get().getMessage());
                throw new RuntimeException("Error from agent: " + errorRef.get().getMessage(), errorRef.get());
            }

            System.out.println("DEBUG: Closing request stream");
            requestObserver.onCompleted();
            System.out.println("DEBUG: Response received, total length: " + responseText.get().length() + " characters");
            return new AgentResponse(responseText.get());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for agent response", e);
        } finally {
            channel.shutdownNow();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Metadata createMetadata(String accessToken) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + accessToken);
        metadata.put(Metadata.Key.of("backend-traceparent", Metadata.ASCII_STRING_MARSHALLER), "00-00000000000000000000000000000000-0000000000000000-00");
        metadata.put(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER), "00-00000000000000000000000000000000-0000000000000000-00");
        metadata.put(Metadata.Key.of("user-agent", Metadata.ASCII_STRING_MARSHALLER), "connect-es/1.7.0");
        metadata.put(Metadata.Key.of("x-cursor-client-version", Metadata.ASCII_STRING_MARSHALLER), "sdk-0.0.0");
        metadata.put(Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER), UUID.randomUUID().toString());
        return metadata;
    }

    private Agent.AgentClientMessage createRunRequest(String userPrompt) {
        Agent.UserMessage userMessage = Agent.UserMessage.newBuilder()
                .setText(userPrompt)
                .build();

        Agent.UserMessageAction userMsgAction = Agent.UserMessageAction.newBuilder()
                .setUserMessage(userMessage)
                .build();

        Agent.ConversationAction action = Agent.ConversationAction.newBuilder()
                .setUserMessageAction(userMsgAction)
                .build();

        Agent.ModelDetails modelDetails = Agent.ModelDetails.newBuilder()
                .setModelName(model)
                .build();

        Agent.ConversationStateStructure state = Agent.ConversationStateStructure.newBuilder().build();
        Agent.McpTools mcpTools = Agent.McpTools.newBuilder().build();
        String conversationId = UUID.randomUUID().toString();

        Agent.AgentRunRequest runRequest = Agent.AgentRunRequest.newBuilder()
                .setConversationState(state)
                .setAction(action)
                .setModelDetails(modelDetails)
                .setMcpTools(mcpTools)
                .setConversationId(conversationId)
                .build();

        return Agent.AgentClientMessage.newBuilder()
                .setRunRequest(runRequest)
                .build();
    }

    private Agent.AgentClientMessage createExecClientMessage(Agent.ExecServerMessage serverMsg) {
        try {
            File rootDir = workingPath.toAbsolutePath().normalize().toFile().getCanonicalFile();

            // Build environment information
            String osName = System.getProperty("os.name", "unknown");
            String osVersion = System.getProperty("os.version", "unknown");
            String osInfo = osName + " " + osVersion;
            String shell = System.getenv("SHELL");
            if (shell == null || shell.isEmpty()) {
                shell = System.getProperty("os.name").toLowerCase().contains("win") ? "cmd" : "sh";
            }

            Agent.RequestContextEnv env = Agent.RequestContextEnv.newBuilder()
                    .setOsVersion(osInfo)
                    .addWorkspacePaths(rootDir.getAbsolutePath())
                    .setShell(shell)
                    .setTerminalsFolder("") // Can be empty for local usage
                    .setAgentSharedNotesFolder("") // Can be empty for local usage
                    .build();

            // Build project layout
            Agent.ProjectLayout rootLayout = buildProjectLayout(rootDir);

            Agent.RequestContext.Builder contextBuilder = Agent.RequestContext.newBuilder()
                .setWorkspacePath(rootDir.getAbsolutePath())
                .setEnv(env)
                .setSharedNotesListing("(No notes directory yet - will be created when you write your first note)")
                .addProjectLayouts(rootLayout);

            Agent.RequestContext ctx = contextBuilder.build();

            Agent.RequestContextSuccess success = Agent.RequestContextSuccess.newBuilder()
                    .setRequestContext(ctx)
                    .build();

            Agent.RequestContextResult result = Agent.RequestContextResult.newBuilder()
                    .setSuccess(success)
                    .build();

            Agent.ExecClientMessage execMsg = Agent.ExecClientMessage.newBuilder()
                    .setRequestContextResult(result)
                    .build();

            return Agent.AgentClientMessage.newBuilder()
                    .setExecClientMessage(execMsg)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create exec client message", e);
        }
    }

    private Agent.ProjectLayout buildProjectLayout(File dir) {
        Agent.ProjectLayout.Builder builder = Agent.ProjectLayout.newBuilder()
                .setAbsPath(dir.getAbsolutePath())
                .setChildrenWereProcessed(true);

        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                if (file.isDirectory()) {
                    // Skip common build/dependency directories
                    String name = file.getName();
                    if (name.equals("target") || name.equals("node_modules") ||
                        name.equals(".git") || name.equals(".idea") ||
                        name.startsWith(".")) {
                        continue;
                    }
                    builder.addChildrenDirs(buildProjectLayout(file));
                } else {
                    builder.addChildrenFiles(Agent.FileEntry.newBuilder()
                            .setName(file.getName())
                            .build());
                }
            }
        }
        return builder.build();
    }

}


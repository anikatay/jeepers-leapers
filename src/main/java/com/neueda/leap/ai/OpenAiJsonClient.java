package com.neueda.leap.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends one system prompt and one user message to OpenAI's Chat Completions API and returns the
 * reply, which must match the given JSON schema (strict structured output).
 */
public class OpenAiJsonClient {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ERROR_BODY_CHARS = 500;
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * One call. reasoningEffort is null for models without reasoning; maxOutputTokens includes any
     * hidden reasoning tokens.
     */
    public record Request(
            String model,
            String systemPrompt,
            String userContent,
            String schemaName,
            Map<String, Object> schema,
            int maxOutputTokens,
            String reasoningEffort) {}

    /** The reply's JSON text and what it cost; model is the version OpenAI reports having used. */
    public record Completion(
            String content,
            String model,
            int promptTokens,
            int completionTokens,
            int reasoningTokens,
            long latencyMs) {}

    private final RestClient restClient;
    private final String apiKey;

    public OpenAiJsonClient(String apiKey, String baseUrl, Duration readTimeout) {
        this(RestClient.builder().requestFactory(timeoutFactory(readTimeout)), apiKey, baseUrl);
    }

    public OpenAiJsonClient(RestClient.Builder builder, String apiKey, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public Completion complete(Request request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ModelCallException("OpenAI API key is not configured");
        }
        if (request.model() == null || request.model().isBlank()) {
            throw new ModelCallException("OpenAI model is not configured");
        }

        long startedAt = System.nanoTime();
        byte[] responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(request))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, response) -> {
                        throw new ModelCallException(
                                "OpenAI returned HTTP " + response.getStatusCode().value() + ": " + errorBody(response));
                    })
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new ModelCallException("Could not reach OpenAI: " + e.getMessage(), e);
        }
        return parse(responseBody, request.model(), (System.nanoTime() - startedAt) / 1_000_000);
    }

    private static byte[] requestBody(Request request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("max_completion_tokens", request.maxOutputTokens());
        if (request.reasoningEffort() != null && !request.reasoningEffort().isBlank()) {
            body.put("reasoning_effort", request.reasoningEffort());
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userContent())));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", request.schemaName(),
                        "strict", true,
                        "schema", request.schema())));
        try {
            return JSON.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new ModelCallException("Could not serialize the request", e);
        }
    }

    private static Completion parse(byte[] responseBody, String requestedModel, long latencyMs) {
        if (responseBody == null || responseBody.length == 0) {
            throw new ModelCallException("OpenAI returned an empty response");
        }
        JsonNode root;
        try {
            root = JSON.readTree(responseBody);
        } catch (IOException e) {
            throw new ModelCallException("OpenAI's response was not valid JSON", e);
        }
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");

        if (message.hasNonNull("refusal")) {
            throw new ModelCallException("OpenAI declined the request: " + message.get("refusal").asText());
        }
        if ("length".equals(choice.path("finish_reason").asText())) {
            throw new ModelCallException("OpenAI's reply was cut off at the output token limit");
        }
        String content = message.path("content").asText("");
        if (content.isBlank()) {
            throw new ModelCallException("OpenAI returned no content");
        }

        JsonNode usage = root.path("usage");
        return new Completion(content, root.path("model").asText(requestedModel),
                usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0),
                usage.path("completion_tokens_details").path("reasoning_tokens").asInt(0), latencyMs);
    }

    private static String errorBody(ClientHttpResponse response) throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).strip();
        return body.length() <= MAX_ERROR_BODY_CHARS ? body : body.substring(0, MAX_ERROR_BODY_CHARS) + "...";
    }

    private static ClientHttpRequestFactory timeoutFactory(Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}

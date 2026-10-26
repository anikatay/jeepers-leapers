package com.neueda.leap.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The transport details shared by every caller; OpenAiTradeAnalyzerTest covers the rest through it. */
class OpenAiJsonClientTest {

    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object", "additionalProperties", false,
            "required", List.of("ok"), "properties", Map.of("ok", Map.of("type", "boolean")));

    private MockRestServiceServer server;
    private OpenAiJsonClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiJsonClient(builder, "test-key", OpenAiJsonClient.DEFAULT_BASE_URL);
    }

    @Test
    void sendsReasoningEffortOnlyWhenSet() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.max_completion_tokens").value(8000))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("probe"))
                .andRespond(withSuccess(reply("{\"ok\":true}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(COMPLETIONS_URL))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andRespond(withSuccess(reply("{\"ok\":true}"), MediaType.APPLICATION_JSON));

        client.complete(request("low"));
        client.complete(request(null));

        server.verify();
    }

    @Test
    void reportsReasoningTokens() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andRespond(withSuccess(JSON.writeValueAsString(Map.of(
                        "model", "test-model-2026-05-18",
                        "usage", Map.of("prompt_tokens", 900, "completion_tokens", 1500,
                                "completion_tokens_details", Map.of("reasoning_tokens", 600)),
                        "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                                "message", Map.of("role", "assistant", "content", "{\"ok\":true}"))))),
                        MediaType.APPLICATION_JSON));

        OpenAiJsonClient.Completion completion = client.complete(request("low"));

        assertEquals("{\"ok\":true}", completion.content());
        assertEquals("test-model-2026-05-18", completion.model());
        assertEquals(900, completion.promptTokens());
        assertEquals(1500, completion.completionTokens());
        assertEquals(600, completion.reasoningTokens());
    }

    @Test
    void reportsHttpErrorsAsModelCallExceptions() {
        server.expect(requestTo(COMPLETIONS_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Unsupported parameter\"}}"));

        ModelCallException e = assertThrows(ModelCallException.class, () -> client.complete(request("low")));

        assertTrue(e.getMessage().contains("HTTP 400"), e.getMessage());
        assertTrue(e.getMessage().contains("Unsupported parameter"), e.getMessage());
    }

    @Test
    void failsWithoutCallingOpenAiWhenTheModelIsMissing() {
        assertThrows(ModelCallException.class, () -> client.complete(new OpenAiJsonClient.Request(
                " ", "system", "user", "probe", SCHEMA, 100, null)));
        server.verify();
    }

    private static OpenAiJsonClient.Request request(String reasoningEffort) {
        return new OpenAiJsonClient.Request("test-model", "system", "user", "probe", SCHEMA, 8000, reasoningEffort);
    }

    private static String reply(String content) throws Exception {
        return JSON.writeValueAsString(Map.of("choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                "message", Map.of("role", "assistant", "content", content)))));
    }
}

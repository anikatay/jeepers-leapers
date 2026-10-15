package com.neueda.leap.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiTradeAnalyzerTest {

    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ANALYSIS = """
            {"verdict":"GOOD","summary":"You sold at a 15.37% gain.",
             "strengths":["Took profit"],"concerns":[],"suggestions":["Keep position sizes similar"]}
            """;

    private MockRestServiceServer server;
    private OpenAiTradeAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        analyzer = new OpenAiTradeAnalyzer(builder, "test-key", "test-model", OpenAiTradeAnalyzer.DEFAULT_BASE_URL);
    }

    @Test
    void sendsKeyModelSchemaAndContext() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.max_completion_tokens").isNumber())
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.verdict.enum.length()").value(4))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"ticker\":\"AAPL\"")))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"realizedPnl\":610.00")))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"executedAt\":\"2026-09-29T14:30:00Z\"")))
                .andRespond(withSuccess(completion(ANALYSIS, null, "stop"), MediaType.APPLICATION_JSON));

        analyzer.analyze(MockTradeContexts.profitableSell());

        server.verify();
    }

    @Test
    void sendsMissingOutcomeAsExplicitNull() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"outcome\":null")))
                .andRespond(withSuccess(completion(ANALYSIS, null, "stop"), MediaType.APPLICATION_JSON));

        analyzer.analyze(MockTradeContexts.freshBuyWithoutOutcome());

        server.verify();
    }

    @Test
    void parsesStructuredReply() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andRespond(withSuccess(completion(ANALYSIS, null, "stop"), MediaType.APPLICATION_JSON));

        TradeAnalysisResult result = analyzer.analyze(MockTradeContexts.profitableSell());

        assertEquals(TradeAnalysisResult.Verdict.GOOD, result.verdict());
        assertEquals("You sold at a 15.37% gain.", result.summary());
        assertEquals(List.of("Took profit"), result.strengths());
        assertTrue(result.concerns().isEmpty());
        assertEquals(List.of("Keep position sizes similar"), result.suggestions());
    }

    @Test
    void reportsTokenUsageAndTheModelUsed() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andRespond(withSuccess(JSON.writeValueAsString(Map.of(
                        "model", "test-model-2026-01-01",
                        "usage", Map.of("prompt_tokens", 812, "completion_tokens", 143),
                        "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                                "message", Map.of("role", "assistant", "content", ANALYSIS))))),
                        MediaType.APPLICATION_JSON));

        OpenAiTradeAnalyzer.Analysis analysis = analyzer.analyzeWithUsage(MockTradeContexts.profitableSell());

        assertEquals(TradeAnalysisResult.Verdict.GOOD, analysis.result().verdict());
        assertEquals("test-model-2026-01-01", analysis.model());
        assertEquals(812, analysis.promptTokens());
        assertEquals(143, analysis.completionTokens());
        assertTrue(analysis.latencyMs() >= 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void reportsHttpErrorsWithStatusAndBody(int status) {
        server.expect(requestTo(COMPLETIONS_URL))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"upstream said no\"}}"));

        TradeAnalysisException e = assertThrows(TradeAnalysisException.class,
                () -> analyzer.analyze(MockTradeContexts.profitableSell()));

        assertTrue(e.getMessage().contains("HTTP " + status), e.getMessage());
        assertTrue(e.getMessage().contains("upstream said no"), e.getMessage());
    }

    @Test
    void reportsRefusal() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andRespond(withSuccess(completion(null, "I can't help with that.", "stop"), MediaType.APPLICATION_JSON));

        TradeAnalysisException e = assertThrows(TradeAnalysisException.class,
                () -> analyzer.analyze(MockTradeContexts.profitableSell()));

        assertTrue(e.getMessage().contains("I can't help with that."), e.getMessage());
    }

    @Test
    void reportsTruncatedReply() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andRespond(withSuccess(completion("{\"verdict\":\"GO", null, "length"), MediaType.APPLICATION_JSON));

        TradeAnalysisException e = assertThrows(TradeAnalysisException.class,
                () -> analyzer.analyze(MockTradeContexts.profitableSell()));

        assertTrue(e.getMessage().contains("cut off"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "this is not json",
            "{\"verdict\":\"BRILLIANT\",\"summary\":\"x\",\"strengths\":[],\"concerns\":[],\"suggestions\":[]}",
            "{\"summary\":\"no verdict\",\"strengths\":[],\"concerns\":[],\"suggestions\":[]}"
    })
    void rejectsMalformedAnalysis(String content) throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andRespond(withSuccess(completion(content, null, "stop"), MediaType.APPLICATION_JSON));

        assertThrows(TradeAnalysisException.class, () -> analyzer.analyze(MockTradeContexts.profitableSell()));
    }

    @Test
    void failsWithoutCallingOpenAiWhenKeyIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer unusedServer = MockRestServiceServer.bindTo(builder).build();
        OpenAiTradeAnalyzer noKey = new OpenAiTradeAnalyzer(builder, " ", "test-model", OpenAiTradeAnalyzer.DEFAULT_BASE_URL);

        TradeAnalysisException e = assertThrows(TradeAnalysisException.class,
                () -> noKey.analyze(MockTradeContexts.profitableSell()));

        assertTrue(e.getMessage().contains("API key"), e.getMessage());
        unusedServer.verify();
    }

    private static String completion(String content, String refusal, String finishReason) throws JsonProcessingException {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        message.put("refusal", refusal);
        return JSON.writeValueAsString(Map.of(
                "choices", List.of(Map.of("index", 0, "message", message, "finish_reason", finishReason))));
    }
}

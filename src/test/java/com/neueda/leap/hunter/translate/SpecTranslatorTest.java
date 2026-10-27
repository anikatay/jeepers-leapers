package com.neueda.leap.hunter.translate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.ai.ModelCallException;
import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.hunter.HunterFixtures;
import com.neueda.leap.hunter.catalog.Metric;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpec.NumericOp;
import com.neueda.leap.hunter.spec.HunterSpec.UnsupportedReason;
import com.neueda.leap.hunter.spec.HunterSpecValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SpecTranslatorTest {

    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROMPT = "Find high-margin AI infrastructure stocks that pulled back more than 10% this month";
    private static final String REPLY = """
            {"name": "High-margin AI infrastructure",
             "quantRules": [{"metric": "grossMarginPct", "op": "gte", "value": 50, "value2": null,
                             "sourceText": "high-margin"}],
             "sectorRule": null,
             "criteria": [{"statement": "Sells products or services used to build or run AI data centers",
                           "sourceText": "AI infrastructure"}],
             "unsupported": [{"sourceText": "pulled back more than 10% this month", "reason": "NO_RECENT_DATA",
                              "message": "Can't check this."}]}
            """;

    private MockRestServiceServer server;
    private SpecTranslator translator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        translator = new SpecTranslator(new OpenAiJsonClient(builder, "test-key", OpenAiJsonClient.DEFAULT_BASE_URL),
                HunterFixtures.SETTINGS, 10);
    }

    @Test
    void sendsTheCatalogCutoffAndAStrictSchema() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("hunter_spec"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.quantRules.items.properties.metric.enum.length()")
                        .value(Metric.values().length))
                .andExpect(jsonPath("$.messages[0].content", containsString("May 18, 2026")))
                .andExpect(jsonPath("$.messages[0].content", containsString("grossMarginPct")))
                .andExpect(jsonPath("$.messages[0].content", containsString("Information Technology")))
                .andExpect(jsonPath("$.messages[1].content", containsString("pulled back more than 10%")))
                .andRespond(withSuccess(reply(REPLY), MediaType.APPLICATION_JSON));

        translator.translate(PROMPT);

        server.verify();
    }

    @Test
    void turnsTheReplyIntoAValidDraft() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL)).andRespond(withSuccess(reply(REPLY), MediaType.APPLICATION_JSON));

        HunterSpec spec = translator.translate(PROMPT).spec();

        assertTrue(HunterSpecValidator.validate(spec).isEmpty());
        assertNull(spec.id());
        assertEquals(PROMPT, spec.prompt());
        assertEquals(10, spec.candidateLimit());
        assertEquals(new HunterSpec.QuantRule("q1", true, "high-margin", Metric.GROSS_MARGIN_PCT, NumericOp.GTE,
                new BigDecimal("50"), null), spec.quantRules().get(0));
        assertEquals("s1", spec.criteria().get(0).id());
        // B2: the time-based phrase becomes a note, never a rule, with a fixed message
        assertEquals(UnsupportedReason.NO_RECENT_DATA, spec.unsupported().get(0).reason());
        assertEquals("The AI's knowledge ends in May 2026, so recent price moves and events can't be checked.",
                spec.unsupported().get(0).message());
    }

    @Test
    void dropsWhatTheModelGotWrong() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL)).andRespond(withSuccess(reply("""
                {"name": "Undervalued AI picks",
                 "quantRules": [{"metric": "ebitdaMargin", "op": "gte", "value": 30, "value2": null, "sourceText": "x"},
                                {"metric": "peRatio", "op": "between", "value": 20, "value2": null, "sourceText": "x"},
                                {"metric": "peRatio", "op": "lte", "value": 25, "value2": 40, "sourceText": "cheap"}],
                 "sectorRule": {"op": "in", "values": [], "sourceText": "tech"},
                 "criteria": [{"statement": " ", "sourceText": "x"}],
                 "unsupported": []}
                """), MediaType.APPLICATION_JSON));

        HunterSpec spec = translator.translate("cheap AI stuff").spec();

        assertEquals(SpecTranslator.DEFAULT_NAME, spec.name());
        assertEquals(1, spec.quantRules().size());
        assertEquals("q1", spec.quantRules().get(0).id());
        assertNull(spec.quantRules().get(0).value2());
        assertNull(spec.sectorRule());
        assertTrue(spec.criteria().isEmpty());
        assertTrue(HunterSpecValidator.validate(spec).isEmpty());
    }

    @Test
    void reusesADraftForTheSameWords() throws Exception {
        server.expect(once(), requestTo(COMPLETIONS_URL)).andRespond(withSuccess(reply(REPLY), MediaType.APPLICATION_JSON));

        HunterSpec first = translator.translate(PROMPT).spec();

        assertEquals(first, translator.cached("  " + PROMPT.toUpperCase() + " ").orElseThrow());
        assertTrue(translator.cached("something else").isEmpty());
        server.verify();
    }

    @Test
    void rejectsAReplyThatIsNotJson() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL)).andRespond(withSuccess(reply("not json"), MediaType.APPLICATION_JSON));

        assertThrows(ModelCallException.class, () -> translator.translate(PROMPT));
    }

    private static String reply(String content) throws Exception {
        return JSON.writeValueAsString(Map.of("model", "test-model",
                "usage", Map.of("prompt_tokens", 1200, "completion_tokens", 300),
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", content)))));
    }
}

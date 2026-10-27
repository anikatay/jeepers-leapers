package com.neueda.leap.hunter.find;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.ai.ModelCallException;
import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.hunter.CheckVerdict;
import com.neueda.leap.hunter.HunterFixtures;
import com.neueda.leap.hunter.HunterLimits;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.spec.HunterSpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CandidateFinderTest {

    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REPLY = """
            {"candidates": [{"ticker": "NVDA",
              "estimates": [{"ruleId": "q1", "value": 75.0, "period": "FY2026"}],
              "criteria": [{"criterionId": "s1", "verdict": "MET", "reason": "Designs GPUs for AI data centers."}],
              "whyItMatched": ["Its accelerators run most AI training."]}]}
            """;

    private MockRestServiceServer server;
    private CandidateFinder finder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        finder = new CandidateFinder(new OpenAiJsonClient(builder, "test-key", OpenAiJsonClient.DEFAULT_BASE_URL),
                HunterFixtures.SETTINGS);
    }

    @Test
    void sendsOnlyEnabledRulesTheCutoffAndHowManyToReturn() throws Exception {
        HunterSpec spec = HunterFixtures.spec(
                List.of(HunterFixtures.grossMarginAtLeast("q1", "50"),
                        new HunterSpec.QuantRule("q2", false, null, com.neueda.leap.hunter.catalog.Metric.PE_RATIO,
                                HunterSpec.NumericOp.LT, new BigDecimal("15"), null)),
                HunterFixtures.sectors(GicsSector.INFORMATION_TECHNOLOGY),
                List.of(HunterFixtures.criterion("s1", "Sells AI data center hardware"),
                        new HunterSpec.Criterion("s2", false, null, "Is founder-led")));

        // B14: exactly one outbound request, to the model, carrying the knowledge cutoff
        server.expect(requestTo(COMPLETIONS_URL))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("hunter_candidates"))
                .andExpect(jsonPath("$.messages[0].content", containsString("May 18, 2026")))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"knowledgeCutoff\":\"2026-05-18\"")))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"maxCandidates\":15")))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"condition\":\"at least 50\"")))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"values\":[\"Information Technology\"]")))
                .andExpect(jsonPath("$.messages[1].content", containsString("Sells AI data center hardware")))
                .andExpect(jsonPath("$.messages[1].content", not(containsString("q2"))))
                .andExpect(jsonPath("$.messages[1].content", not(containsString("founder-led"))))
                .andRespond(withSuccess(reply(REPLY), MediaType.APPLICATION_JSON));

        finder.find(spec);

        server.verify();
    }

    @Test
    void parsesTheCandidates() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL)).andRespond(withSuccess(reply(REPLY), MediaType.APPLICATION_JSON));

        CandidateFinder.Result result = finder.find(HunterFixtures.aiInfrastructure());

        CandidateFinder.Candidate nvda = result.candidates().get(0);
        assertEquals("NVDA", nvda.ticker());
        assertEquals(new CandidateFinder.Estimate("q1", new BigDecimal("75.0"), "FY2026"), nvda.estimates().get(0));
        assertEquals(CheckVerdict.MET, nvda.criteria().get(0).verdict());
        assertEquals(1, result.completion().completionTokens());
    }

    @Test
    void asksForAtMostTwentyCompanies() {
        HunterSpec base = HunterFixtures.aiInfrastructure();
        HunterSpec fifteen = new HunterSpec(base.specVersion(), null, base.name(), base.prompt(), base.universe(),
                base.quantRules(), null, base.criteria(), List.of(), HunterLimits.MAX_CANDIDATES);

        assertEquals(20, CandidateFinder.candidatesToAskFor(fifteen));
        assertEquals(15, CandidateFinder.candidatesToAskFor(base));
    }

    @Test
    void rejectsAReplyThatIsNotJson() throws Exception {
        server.expect(requestTo(COMPLETIONS_URL)).andRespond(withSuccess(reply("{\"candidates\": [1,"),
                MediaType.APPLICATION_JSON));

        assertThrows(ModelCallException.class, () -> finder.find(HunterFixtures.aiInfrastructure()));
    }

    private static String reply(String content) throws Exception {
        return JSON.writeValueAsString(Map.of("model", "test-model",
                "usage", Map.of("prompt_tokens", 900, "completion_tokens", 1),
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", content)))));
    }
}

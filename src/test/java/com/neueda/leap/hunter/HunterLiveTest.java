package com.neueda.leap.hunter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.hunter.check.CandidateChecks;
import com.neueda.leap.hunter.find.CandidateFinder;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpec.UnsupportedReason;
import com.neueda.leap.hunter.spec.HunterSpecValidator;
import com.neueda.leap.hunter.translate.SpecTranslator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drafts and runs the example prompt from section 4.3 of the spec against the real API, two calls
 * in all, and prints what comes back. Skipped unless OPENAI_API_KEY is set; HUNTER_OPENAI_MODEL
 * overrides the model.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class HunterLiveTest {

    private static final String PROMPT = "Find high-margin AI infrastructure stocks that pulled back more than 10% this month";
    private static final ObjectMapper PRINTER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Test
    void draftsAndRunsTheExamplePrompt() throws Exception {
        String model = System.getenv().getOrDefault("HUNTER_OPENAI_MODEL", "gpt-6-luna");
        HunterModelSettings settings = new HunterModelSettings(model, LocalDate.parse("2026-05-18"), "low", 8000);
        OpenAiJsonClient client = new OpenAiJsonClient(System.getenv("OPENAI_API_KEY"),
                OpenAiJsonClient.DEFAULT_BASE_URL, Duration.ofSeconds(120));

        SpecTranslator.Translation draft = new SpecTranslator(client, settings, 10).translate(PROMPT);
        print("draft", draft.spec(), draft.completion());

        assertTrue(HunterSpecValidator.validate(draft.spec()).isEmpty());
        assertTrue(draft.spec().unsupported().stream().anyMatch(c -> c.reason() == UnsupportedReason.NO_RECENT_DATA),
                "the time-based phrase should be a NO_RECENT_DATA note");

        HunterSpec spec = draft.spec();
        CandidateFinder.Result found = new CandidateFinder(client, settings).find(spec);
        CandidateChecks.Outcome outcome = CandidateChecks.apply(spec, found.candidates(), new Universe());
        print("candidates", found.candidates(), found.completion());
        System.out.printf("kept %d, dropped not in universe %d, dropped by rules %d, source guard %d, advice guard %d%n",
                outcome.kept().size(), outcome.droppedNotInUniverse(), outcome.droppedByRules(),
                outcome.sourceGuardRemovals(), outcome.adviceGuardRemovals());

        assertFalse(found.candidates().isEmpty());
    }

    private static void print(String title, Object value, OpenAiJsonClient.Completion completion) throws Exception {
        System.out.printf("%n===== %s (model %s, %d prompt + %d completion tokens, %d reasoning, %d ms) =====%n%s%n",
                title, completion.model(), completion.promptTokens(), completion.completionTokens(),
                completion.reasoningTokens(), completion.latencyMs(), PRINTER.writeValueAsString(value));
    }
}

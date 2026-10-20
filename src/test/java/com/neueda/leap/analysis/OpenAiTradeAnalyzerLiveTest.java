package com.neueda.leap.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Calls the real OpenAI API with the mock contexts and prints what comes back.
 * Skipped unless OPENAI_API_KEY is set; OPENAI_MODEL overrides the model.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiTradeAnalyzerLiveTest {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private static final ObjectMapper PRINTER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    static Stream<Arguments> scenarios() {
        return MockTradeContexts.scenarios().entrySet().stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void analysesMockTrade(String scenario, TradeAnalysisContext context) throws Exception {
        String model = System.getenv("OPENAI_MODEL");
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        OpenAiTradeAnalyzer analyzer = new OpenAiTradeAnalyzer(
                System.getenv("OPENAI_API_KEY"), model, OpenAiTradeAnalyzer.DEFAULT_BASE_URL);

        TradeAnalysisResult result = analyzer.analyze(context);

        System.out.printf("%n===== %s (model: %s) =====%n", scenario, model);
        System.out.println("--- context sent ---");
        System.out.println(PRINTER.writeValueAsString(context));
        System.out.println("--- analysis returned ---");
        System.out.println(PRINTER.writeValueAsString(result));

        assertNotNull(result.verdict());
        assertFalse(result.summary().isBlank());
        assertNotNull(result.strengths());
        assertNotNull(result.concerns());
        assertNotNull(result.suggestions());
    }
}

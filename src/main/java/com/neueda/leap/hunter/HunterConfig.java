package com.neueda.leap.hunter;

import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.analysis.AnalysisRateLimiter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDate;

@Configuration
public class HunterConfig {

    // A run asks for up to 20 companies in one reply, which can take well over a minute.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    @Bean
    public HunterModelSettings hunterModelSettings(
            @Value("${hunter.openai.model}") String model,
            @Value("${hunter.knowledge-cutoff}") String knowledgeCutoff,
            @Value("${hunter.reasoning-effort}") String reasoningEffort,
            @Value("${hunter.max-output-tokens}") int maxOutputTokens) {
        return new HunterModelSettings(model, LocalDate.parse(knowledgeCutoff), reasoningEffort, maxOutputTokens);
    }

    @Bean
    public OpenAiJsonClient hunterOpenAiClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url}") String baseUrl) {
        return new OpenAiJsonClient(apiKey, baseUrl, READ_TIMEOUT);
    }

    @Bean
    public HunterRateLimits hunterRateLimits(
            @Value("${hunter.rate-limit.draft.global-per-minute}") int draftGlobal,
            @Value("${hunter.rate-limit.draft.per-client-per-minute}") int draftPerClient,
            @Value("${hunter.rate-limit.run.global-per-minute}") int runGlobal,
            @Value("${hunter.rate-limit.run.per-client-per-minute}") int runPerClient) {
        return new HunterRateLimits(
                new AnalysisRateLimiter(draftGlobal, draftPerClient),
                new AnalysisRateLimiter(runGlobal, runPerClient));
    }
}

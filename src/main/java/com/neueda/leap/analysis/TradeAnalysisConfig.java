package com.neueda.leap.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TradeAnalysisConfig {

    @Bean
    public OpenAiTradeAnalyzer openAiTradeAnalyzer(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model,
            @Value("${openai.base-url}") String baseUrl) {
        return new OpenAiTradeAnalyzer(apiKey, model, baseUrl);
    }
}

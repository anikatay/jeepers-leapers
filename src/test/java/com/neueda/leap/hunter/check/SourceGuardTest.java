package com.neueda.leap.hunter.check;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "The CEO said \"AI demand is unprecedented\".",
            "Management described “strong demand” for accelerators.",
            "Its latest 10-K describes data center growth.",
            "In its 10-q the company reported higher margins.",
            "Its SEC filings show recurring revenue.",
            "On the earnings call, management raised guidance.",
            "According to the company, most revenue is recurring.",
            "A press release announced a new chip.",
            "The transcript mentions AI twice.",
            "Source: company website."
    })
    void flagsQuotesAndNamedSources(String text) {
        assertTrue(SourceGuard.flags(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Designs GPUs widely used to train and run AI models.",
            "The company's networking business serves large data centers.",
            "Revenue is largely recurring subscription software."
    })
    void allowsPlainDescriptions(String text) {
        assertFalse(SourceGuard.flags(text), text);
    }
}

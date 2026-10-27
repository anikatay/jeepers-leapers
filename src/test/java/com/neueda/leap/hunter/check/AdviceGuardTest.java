package com.neueda.leap.hunter.check;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdviceGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Investors should buy before the next product cycle.",
            "Consider adding to your position.",
            "Analysts have a buy rating on the stock.",
            "The price target implies more growth.",
            "The stock looks undervalued.",
            "Shares seem overvalued against peers.",
            "There is significant upside here.",
            "A bargain at this valuation.",
            "An attractive entry point.",
            "A good investment for the long term.",
            "We recommend this company.",
            "Top analysts recommended it."
    })
    void flagsAdviceLanguage(String text) {
        assertTrue(AdviceGuard.flags(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Sells networking equipment and software to data centers.",
            "The company buys back shares regularly.",
            "Holds a large share of the accelerator market."
    })
    void allowsCompanyDescriptions(String text) {
        assertFalse(AdviceGuard.flags(text), text);
    }
}

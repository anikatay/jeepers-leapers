package com.neueda.leap.analysis;

import java.util.List;

public record TradeAnalysisResult(
        Verdict verdict,
        String summary,
        List<String> strengths,
        List<String> concerns,
        List<String> suggestions) {

    public enum Verdict { GOOD, MIXED, POOR, TOO_EARLY }
}

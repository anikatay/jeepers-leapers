package com.neueda.leap.hunter.check;

import java.util.List;
import java.util.regex.Pattern;

/** Flags model-written text that reads as investment advice (section 5.3 of the spec). */
public final class AdviceGuard {

    private static final List<Pattern> PATTERNS = List.of(
            "\\bshould (buy|sell|hold|own|add|invest)\\b",
            "\\bconsider (buying|selling|adding|holding|investing)\\b",
            "\\b(buy|sell|hold) rating\\b",
            "\\bstrong buy\\b",
            "\\bprice targets?\\b",
            "\\bundervalued\\b",
            "\\bovervalued\\b",
            "\\bupside\\b",
            "\\bbargains?\\b",
            "\\battractive (entry|valuation|price)\\b",
            "\\bgood investment\\b",
            "\\brecommend\\w*\\b").stream()
            .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
            .toList();

    private AdviceGuard() {}

    public static boolean flags(String text) {
        return text != null && PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}

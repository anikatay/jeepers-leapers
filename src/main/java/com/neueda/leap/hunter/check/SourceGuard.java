package com.neueda.leap.hunter.check;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The model has no documents in front of it, so any quote or named source in its text was made up.
 * Flags text that quotes or cites one (section 5.3 of the spec).
 */
public final class SourceGuard {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("[\"“”]"),
            Pattern.compile("\\b(10-K|10-Q|8-K)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bfilings?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btranscripts?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bearnings calls?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpress releases?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\baccording to\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsource:", Pattern.CASE_INSENSITIVE));

    private SourceGuard() {}

    public static boolean flags(String text) {
        return text != null && PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}

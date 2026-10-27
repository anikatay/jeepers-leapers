package com.neueda.leap.hunter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Which model Hunter asks and where its knowledge ends. knowledgeCutoff must be changed whenever
 * the model changes: it is sent to the model and shown on every result.
 */
public record HunterModelSettings(
        String model,
        LocalDate knowledgeCutoff,
        String reasoningEffort,
        int maxOutputTokens) {

    /** "May 18, 2026", for prompts. */
    public String cutoffDate() {
        return knowledgeCutoff.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
    }

    /** "May 2026", for messages shown to the user. */
    public String cutoffMonth() {
        return knowledgeCutoff.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
    }
}

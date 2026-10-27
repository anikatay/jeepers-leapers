package com.neueda.leap.hunter;

import java.time.LocalDate;
import java.util.List;

/** GET /api/hunter/catalog: what a spec can use, and where the answers come from. */
public record HunterCatalog(
        List<MetricInfo> metrics,
        List<String> sectors,
        String model,
        LocalDate knowledgeCutoff,
        String universeAsOf,
        int universeSize,
        Limits limits) {

    public record MetricInfo(String id, String label, String unit, String definition) {}

    public record Limits(
            int maxPromptLength,
            int maxQuantRules,
            int maxCriteria,
            int maxCandidates,
            int defaultCandidates,
            int maxSpecs,
            int maxWatchlistItems) {}
}

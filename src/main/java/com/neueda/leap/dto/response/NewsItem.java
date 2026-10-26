package com.neueda.leap.dto.response;

import java.util.List;

/**
 * One headline from the market data service. publishedAt is an ISO-8601 instant, or null when
 * the provider did not supply a usable timestamp.
 */
public record NewsItem(
        String id,
        String title,
        String publisher,
        String link,
        String publishedAt,
        List<String> relatedTickers
) {}

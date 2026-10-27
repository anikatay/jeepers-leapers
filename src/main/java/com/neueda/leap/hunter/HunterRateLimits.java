package com.neueda.leap.hunter;

import com.neueda.leap.analysis.AnalysisRateLimiter;

/**
 * Per-minute caps on the two Hunter calls that reach the model. Kept out of the bean registry so
 * TradeController still gets the single AnalysisRateLimiter bean.
 */
public record HunterRateLimits(AnalysisRateLimiter drafts, AnalysisRateLimiter runs) {}

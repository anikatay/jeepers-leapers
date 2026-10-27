package com.neueda.leap.hunter;

/** The limits in docs/specs/watchlist-hunter.md, sections 4.1, 5 and 7. */
public final class HunterLimits {

    public static final int MAX_PROMPT_LENGTH = 500;
    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_STATEMENT_LENGTH = 200;
    public static final int MAX_SOURCE_TEXT_LENGTH = 500;
    public static final int MAX_QUANT_RULES = 8;
    public static final int MAX_CRITERIA = 5;
    public static final int MAX_CANDIDATES = 15;
    public static final int DEFAULT_CANDIDATES = 10;
    /** Asked for on top of candidateLimit, to make up for candidates the checks remove. */
    public static final int EXTRA_CANDIDATES = 5;
    public static final int MAX_BULLETS = 3;
    public static final int MAX_SPECS_PER_USER = 20;
    public static final int RUNS_KEPT_PER_SPEC = 10;
    public static final int MAX_WATCHLIST_ITEMS = 100;

    private HunterLimits() {}
}

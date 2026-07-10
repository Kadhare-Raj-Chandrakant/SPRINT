package com.bot.bot.domain;

public record TriageResult(
    Tier tier,
    boolean securityFlag,
    SuggestedAction suggestedAction
) {
    public enum Tier { GREEN, YELLOW, RED }
    public enum SuggestedAction { REVIEW_AND_MERGE, MANUAL_CHECK, CONSIDER_CLOSING }
}

package com.bitext.lucene.analysis.en.model;

/** Bounds the context retained while resolving one token stream. */
public record ContextBudget(int maxPendingTokens, int maxPendingSurfaceChars) {
  public ContextBudget {
    if (maxPendingTokens < 1 || maxPendingSurfaceChars < 1) {
      throw new IllegalArgumentException("positive context budget required");
    }
  }
}

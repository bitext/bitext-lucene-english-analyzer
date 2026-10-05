package com.bitext.lucene.analysis.en.model;

import java.util.List;

public record MorphologySnapshot(
    MorphologyBinding binding,
    String lookupKey,
    LookupStatus lookupStatus,
    List<CandidateAnalysis> candidates,
    boolean keywordAtAnalysis,
    String resourceId,
    TokenCase tokenCase) {
  public MorphologySnapshot {
    if (binding == null || lookupStatus == null || resourceId == null || tokenCase == null)
      throw new IllegalArgumentException("incomplete morphology snapshot");
    candidates = List.copyOf(candidates);
    if (lookupStatus == LookupStatus.FOUND && candidates.isEmpty())
      throw new IllegalArgumentException("FOUND snapshot requires candidates");
    if (lookupStatus != LookupStatus.FOUND && !candidates.isEmpty())
      throw new IllegalArgumentException("non-found snapshot cannot have candidates");
  }
}

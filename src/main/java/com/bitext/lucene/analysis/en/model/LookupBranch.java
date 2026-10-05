package com.bitext.lucene.analysis.en.model;

import java.util.List;
import java.util.Objects;

/** Immutable provenance for one requested lexical branch before contextual selection. */
public record LookupBranch(
    Role role,
    String resourceId,
    String requestedSpelling,
    boolean explicitlyTransformed,
    LookupStatus status,
    List<CandidateAnalysis> candidates) {
  public enum Role {
    ORDINARY_EXACT,
    ORDINARY_LOWERCASE,
    ENTITY_EXACT
  }

  public LookupBranch {
    Objects.requireNonNull(role);
    Objects.requireNonNull(resourceId);
    Objects.requireNonNull(requestedSpelling);
    Objects.requireNonNull(status);
    candidates = List.copyOf(candidates);
    if (status == LookupStatus.FOUND && candidates.isEmpty())
      throw new IllegalArgumentException("found branch requires candidates");
    if (status != LookupStatus.FOUND && !candidates.isEmpty())
      throw new IllegalArgumentException("miss branch cannot retain candidates");
  }
}

package com.bitext.lucene.analysis.en.model;

import java.util.List;

/** Immutable per-token lookup evidence consumed by a compatible selector. */
public record LookupEvidence(
    List<LookupBranch> branches, MorphologySnapshot snapshot, boolean selectionPending) {
  public LookupEvidence {
    branches = List.copyOf(branches);
  }

  public LookupEvidence(List<LookupBranch> branches) {
    this(branches, null, false);
  }

  public static LookupEvidence none() {
    return new LookupEvidence(List.of(), null, false);
  }

  /** Entity-aware branch evidence cannot be emitted until its selector resolves this snapshot. */
  public static LookupEvidence pending(MorphologySnapshot snapshot, List<LookupBranch> branches) {
    return new LookupEvidence(branches, java.util.Objects.requireNonNull(snapshot), true);
  }

  public LookupEvidence complete(MorphologySnapshot expectedSnapshot) {
    if (snapshot != expectedSnapshot)
      throw new IllegalStateException("lookup evidence belongs to a different morphology snapshot");
    return new LookupEvidence(branches, snapshot, false);
  }

  public LookupBranch required(LookupBranch.Role role) {
    return branches.stream()
        .filter(branch -> branch.role() == role)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("missing lookup evidence branch: " + role));
  }
}

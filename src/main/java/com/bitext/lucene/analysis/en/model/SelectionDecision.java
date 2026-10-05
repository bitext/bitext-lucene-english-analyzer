package com.bitext.lucene.analysis.en.model;

import java.util.List;
import java.util.Objects;

public final class SelectionDecision {
  public enum Kind {
    ALL,
    SUBSET,
    SURFACE_ONLY
  }

  public enum Reason {
    UPSTREAM_KEYWORD,
    EXPLICIT_SELECTION,
    CONTEXT_POLICY,
    NO_SURVIVING_ANALYSIS
  }

  private final MorphologySnapshot snapshot;
  private final Kind kind;
  private final List<Integer> ordinals;
  private final Reason reason;

  private SelectionDecision(MorphologySnapshot s, Kind k, List<Integer> o, Reason r) {
    snapshot = Objects.requireNonNull(s);
    kind = k;
    ordinals = List.copyOf(o);
    reason = r;
  }

  public static SelectionDecision all(MorphologySnapshot s) {
    return new SelectionDecision(s, Kind.ALL, List.of(), null);
  }

  public static SelectionDecision subset(MorphologySnapshot s, List<Integer> o) {
    if (o == null || o.isEmpty()) throw new IllegalArgumentException("SUBSET must not be empty");
    int last = -1;
    for (int n : o)
      if (n <= last || n < 0 || n >= s.candidates().size())
        throw new IllegalArgumentException("invalid candidate ordinal");
      else last = n;
    return new SelectionDecision(s, Kind.SUBSET, o, null);
  }

  public static SelectionDecision surfaceOnly(MorphologySnapshot s, Reason r) {
    return new SelectionDecision(s, Kind.SURFACE_ONLY, List.of(), Objects.requireNonNull(r));
  }

  public MorphologySnapshot snapshot() {
    return snapshot;
  }

  public Kind kind() {
    return kind;
  }

  public List<Integer> ordinals() {
    return ordinals;
  }

  public Reason reason() {
    return reason;
  }

  public List<CandidateAnalysis> candidates() {
    return kind == Kind.ALL
        ? snapshot.candidates()
        : kind == Kind.SUBSET
            ? ordinals.stream().map(snapshot.candidates()::get).toList()
            : List.of();
  }
}

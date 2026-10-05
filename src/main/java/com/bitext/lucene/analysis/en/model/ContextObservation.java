package com.bitext.lucene.analysis.en.model;

/** Immutable outcome of observing the bounded context around a token. */
public record ContextObservation(
    Scope scope,
    Status status,
    Style style,
    int casedTokens,
    int lower,
    int initialCapital,
    int upper,
    int mixed,
    boolean complete,
    String detectorId,
    String policyId,
    int numericTokens,
    int alphanumericTokens) {
  public enum Scope {
    TOKEN_STREAM,
    SUPPLIED_SENTENCES
  }

  public enum Status {
    NOT_EVALUATED,
    COMPLETE,
    LIMIT_REACHED,
    NON_LINEAR_INPUT,
    MISSING_BOUNDARY_METADATA
  }

  public enum Style {
    UNDETERMINED,
    ORDINARY_PROSE,
    TITLE_CASE,
    ALL_CAPS,
    MIXED
  }

  public ContextObservation(
      Scope scope,
      Status status,
      Style style,
      int casedTokens,
      int lower,
      int initialCapital,
      int upper,
      int mixed,
      boolean complete,
      String detectorId,
      String policyId) {
    this(
        scope,
        status,
        style,
        casedTokens,
        lower,
        initialCapital,
        upper,
        mixed,
        complete,
        detectorId,
        policyId,
        0,
        0);
  }

  public static ContextObservation notEvaluated() {
    return new ContextObservation(
        Scope.TOKEN_STREAM,
        Status.NOT_EVALUATED,
        Style.UNDETERMINED,
        0,
        0,
        0,
        0,
        0,
        false,
        "",
        " ");
  }
}

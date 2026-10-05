package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.ContextObservation;
import java.util.Objects;

/** Immutable context supplied to one selection-policy invocation. */
record SelectionContext(ContextObservation observation, ContextSpan span, int currentIndex) {
  public SelectionContext {
    Objects.requireNonNull(observation);
    Objects.requireNonNull(span);
    if (currentIndex < 0 || currentIndex >= span.tokens().size())
      throw new IllegalArgumentException("invalid context token index");
  }

  public ContextToken currentToken() {
    return span.tokens().get(currentIndex);
  }
}

package com.bitext.lucene.analysis.en;

import java.util.List;

/** Immutable, bounded context evidence; never exposes filter-owned mutable state. */
record ContextSpan(List<ContextToken> tokens) {
  public ContextSpan {
    tokens = List.copyOf(tokens);
  }
}

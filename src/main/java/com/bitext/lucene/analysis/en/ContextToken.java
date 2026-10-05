package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.MorphologySnapshot;
import com.bitext.lucene.analysis.en.model.SelectionDecision;
import java.util.Objects;

/** Immutable token evidence made available to bounded context extensions. */
record ContextToken(
    String surface, MorphologySnapshot morphology, SelectionDecision selection) {
  public ContextToken {
    Objects.requireNonNull(surface);
    Objects.requireNonNull(morphology);
    Objects.requireNonNull(selection);
  }
}

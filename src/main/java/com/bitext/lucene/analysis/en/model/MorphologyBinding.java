package com.bitext.lucene.analysis.en.model;

/** Stable token identity retained with morphology results. */
public record MorphologyBinding(
    String surface, int startOffset, int endOffset, int positionIncrement, int positionLength) {
  public MorphologyBinding {
    if (surface == null
        || startOffset < 0
        || endOffset < startOffset
        || positionIncrement < 0
        || positionLength < 1) {
      throw new IllegalArgumentException("invalid morphology binding");
    }
  }
}

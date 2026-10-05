package com.bitext.lucene.analysis.en;

/** Tokenizer choice for convenience analyzers. */
enum SentenceProvider {
  NONE,
  SENTENCE_AWARE;

  public static SentenceProvider parse(String value) {
    if ("none".equals(value)) return NONE;
    if ("sentence_aware".equals(value)) return SENTENCE_AWARE;
    throw new IllegalArgumentException("unknown sentence provider: " + value);
  }
}

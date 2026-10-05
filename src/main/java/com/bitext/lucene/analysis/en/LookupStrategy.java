package com.bitext.lucene.analysis.en;

/** Explicit lexical request policy for an exact-key resource. */
enum LookupStrategy {
  EXACT,
  LOWERCASE_CONTROL,
  ENTITY_AWARE;

  public static LookupStrategy parse(String value) {
    if (value == null) throw new IllegalArgumentException("lookup strategy is required");
    return switch (value) {
      case "exact" -> EXACT;
      case "lowercase_control" -> LOWERCASE_CONTROL;
      case "entity_aware" -> ENTITY_AWARE;
      default -> throw new IllegalArgumentException("unknown lookup strategy: " + value);
    };
  }
}

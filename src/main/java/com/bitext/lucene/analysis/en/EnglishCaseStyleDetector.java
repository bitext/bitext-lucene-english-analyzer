package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.ContextObservation;
import com.bitext.lucene.analysis.en.model.TokenCase;
import java.util.List;
import java.util.Set;

/** Detects the orthographic style of a bounded English context. */
final class EnglishCaseStyleDetector implements CaseStyleDetector {
  public static final String ID = "english-case-style-v1";
  public static final EnglishCaseStyleDetector INSTANCE = new EnglishCaseStyleDetector();
  private static final Set<String> CONNECTORS =
      Set.of("a", "an", "the", "and", "or", "of", "to", "with", "in", "on", "for");

  private EnglishCaseStyleDetector() {}

  @Override
  public String id() {
    return ID;
  }

  @Override
  public ContextObservation.Style observe(ContextSpan span) {
    return observe(span.tokens().stream().map(ContextToken::surface).toList());
  }

  /** Convenience helper for direct detector tests; extensions receive ContextSpan. */
  public ContextObservation.Style observe(List<String> terms) {
    int cased = 0, upper = 0, initial = 0;
    boolean allUpper = true, title = true;
    int titleWords = 0;
    for (String term : terms) {
      TokenCase c = EnglishMorphologyFilter.tokenCase(term);
      if (c == TokenCase.NO_CASE) continue;
      cased++;
      allUpper &= c == TokenCase.UPPER;
      boolean connector = CONNECTORS.contains(EnglishText.lower(term));
      if (connector) {
        title &= (c == TokenCase.LOWER || c == TokenCase.INITIAL_CAPITAL);
      } else {
        title &= c == TokenCase.INITIAL_CAPITAL;
        if (c == TokenCase.INITIAL_CAPITAL) titleWords++;
      }
      if (c == TokenCase.UPPER) upper++;
      if (c == TokenCase.INITIAL_CAPITAL) initial++;
    }
    if (cased < 3) return ContextObservation.Style.UNDETERMINED;
    if (allUpper) return ContextObservation.Style.ALL_CAPS;
    if (title && titleWords >= 2) return ContextObservation.Style.TITLE_CASE;
    TokenCase first = firstCased(terms);
    boolean ordinary = (first == TokenCase.LOWER || first == TokenCase.INITIAL_CAPITAL);
    boolean afterFirst = false;
    for (String term : terms) {
      TokenCase c = EnglishMorphologyFilter.tokenCase(term);
      if (c == TokenCase.NO_CASE) continue;
      if (!afterFirst) {
        afterFirst = true;
        continue;
      }
      ordinary &= c == TokenCase.LOWER;
    }
    if (ordinary) return ContextObservation.Style.ORDINARY_PROSE;
    return ContextObservation.Style.MIXED;
  }

  private static TokenCase firstCased(List<String> terms) {
    for (String term : terms) {
      TokenCase c = EnglishMorphologyFilter.tokenCase(term);
      if (c != TokenCase.NO_CASE) return c;
    }
    return TokenCase.NO_CASE;
  }
}

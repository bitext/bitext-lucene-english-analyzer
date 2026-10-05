package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.ContextObservation;
import com.bitext.lucene.analysis.en.model.MorphologySnapshot;
import com.bitext.lucene.analysis.en.model.SelectionDecision;
import com.bitext.lucene.analysis.en.model.TokenCase;

/** Built-in context selection policies supported by the English analyzer. */
final class EnglishSelectionPolicies {
  private EnglishSelectionPolicies() {}

  public static EnglishSelectionPolicy named(String id) {
    return switch (id == null ? "KEEP_ALL" : id) {
      case "KEEP_ALL" -> KEEP_ALL;
      case "surface_only" -> SURFACE_ONLY;
      default -> throw new IllegalArgumentException("unknown selection policy: " + id);
    };
  }

  public static final EnglishSelectionPolicy KEEP_ALL =
      new EnglishSelectionPolicy() {
        @Override
        public String id() {
          return "KEEP_ALL";
        }

        @Override
        public SelectionDecision select(
            SelectionContext o, MorphologySnapshot m, SelectionDecision c) {
          return c;
        }
      };
  public static final EnglishSelectionPolicy SURFACE_ONLY =
      new EnglishSelectionPolicy() {
        @Override
        public String id() {
          return "surface_only";
        }

        @Override
        public SelectionDecision select(
            SelectionContext o, MorphologySnapshot m, SelectionDecision c) {
          return o.observation().status() == ContextObservation.Status.COMPLETE
                  && m.tokenCase() == TokenCase.INITIAL_CAPITAL
                  && !m.keywordAtAnalysis()
              ? SelectionDecision.surfaceOnly(m, SelectionDecision.Reason.CONTEXT_POLICY)
              : c;
        }
      };
}

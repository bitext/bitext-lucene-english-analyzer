package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.CandidateAnalysis;
import java.util.List;

/** Immutable read-only morphology resource used by a single analysis stream. */
interface EnglishLexicon {
  String resourceId();

  String normalizationId();

  List<CandidateAnalysis> lookup(String key);
}

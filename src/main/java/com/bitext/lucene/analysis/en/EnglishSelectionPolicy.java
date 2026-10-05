package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.MorphologySnapshot;
import com.bitext.lucene.analysis.en.model.SelectionDecision;

/** Immutable host-neutral policy. Results are validated by EnglishContextFilter. */
interface EnglishSelectionPolicy {
  String id();

  SelectionDecision select(
      SelectionContext context, MorphologySnapshot morphology, SelectionDecision current);
}

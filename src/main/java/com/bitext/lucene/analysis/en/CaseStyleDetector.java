package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.ContextObservation;

/** Immutable, host-neutral detector for a bounded context span. */
interface CaseStyleDetector {
  String id();

  ContextObservation.Style observe(ContextSpan span);
}

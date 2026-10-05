package com.bitext.lucene.analysis.en.attributes;

import com.bitext.lucene.analysis.en.model.LookupEvidence;
import org.apache.lucene.util.Attribute;

/** Carries immutable lookup provenance until selection or emission completes. */
public interface LookupEvidenceAttribute extends Attribute {
  LookupEvidence getEvidence();

  void setEvidence(LookupEvidence evidence);
}

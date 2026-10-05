package com.bitext.lucene.analysis.en.attributes;

import com.bitext.lucene.analysis.en.model.LookupEvidence;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public final class LookupEvidenceAttributeImpl extends AttributeImpl
    implements LookupEvidenceAttribute {
  private LookupEvidence evidence;

  @Override
  public LookupEvidence getEvidence() {
    return evidence;
  }

  @Override
  public void setEvidence(LookupEvidence value) {
    evidence = value;
  }

  @Override
  public void clear() {
    evidence = null;
  }

  @Override
  public void copyTo(AttributeImpl target) {
    ((LookupEvidenceAttribute) target).setEvidence(evidence);
  }

  @Override
  public void reflectWith(AttributeReflector reflector) {
    reflector.reflect(LookupEvidenceAttribute.class, "evidence", evidence);
  }
}

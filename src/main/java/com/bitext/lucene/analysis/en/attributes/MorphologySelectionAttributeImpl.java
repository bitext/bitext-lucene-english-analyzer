package com.bitext.lucene.analysis.en.attributes;

import com.bitext.lucene.analysis.en.model.SelectionDecision;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public final class MorphologySelectionAttributeImpl extends AttributeImpl
    implements MorphologySelectionAttribute {
  private SelectionDecision decision;

  public SelectionDecision getDecision() {
    return decision;
  }

  public void setDecision(SelectionDecision value) {
    decision = value;
  }

  @Override
  public void clear() {
    decision = null;
  }

  @Override
  public void copyTo(AttributeImpl target) {
    ((MorphologySelectionAttribute) target).setDecision(decision);
  }

  @Override
  public void reflectWith(AttributeReflector r) {
    r.reflect(MorphologySelectionAttribute.class, "decision", decision);
  }
}

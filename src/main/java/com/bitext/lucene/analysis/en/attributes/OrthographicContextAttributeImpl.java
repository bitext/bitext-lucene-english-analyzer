package com.bitext.lucene.analysis.en.attributes;

import com.bitext.lucene.analysis.en.model.ContextObservation;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public final class OrthographicContextAttributeImpl extends AttributeImpl
    implements OrthographicContextAttribute {
  private ContextObservation observation = ContextObservation.notEvaluated();

  public ContextObservation getObservation() {
    return observation;
  }

  public void setObservation(ContextObservation value) {
    observation = value == null ? ContextObservation.notEvaluated() : value;
  }

  @Override
  public void clear() {
    observation = ContextObservation.notEvaluated();
  }

  @Override
  public void copyTo(AttributeImpl target) {
    ((OrthographicContextAttribute) target).setObservation(observation);
  }

  @Override
  public void reflectWith(AttributeReflector r) {
    r.reflect(OrthographicContextAttribute.class, "observation", observation);
  }
}

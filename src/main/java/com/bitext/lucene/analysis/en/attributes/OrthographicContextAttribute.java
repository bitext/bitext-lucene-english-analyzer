package com.bitext.lucene.analysis.en.attributes;

import com.bitext.lucene.analysis.en.model.ContextObservation;
import org.apache.lucene.util.Attribute;

public interface OrthographicContextAttribute extends Attribute {
  ContextObservation getObservation();

  void setObservation(ContextObservation observation);
}

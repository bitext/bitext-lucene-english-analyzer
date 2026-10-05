package com.bitext.lucene.analysis.en.attributes;

import com.bitext.lucene.analysis.en.model.SelectionDecision;
import org.apache.lucene.util.Attribute;

public interface MorphologySelectionAttribute extends Attribute {
  SelectionDecision getDecision();

  void setDecision(SelectionDecision decision);
}

package com.bitext.lucene.analysis.en.attributes;

import com.bitext.lucene.analysis.en.model.MorphologySnapshot;
import org.apache.lucene.util.Attribute;

public interface MorphologyAttribute extends Attribute {
  MorphologySnapshot getSnapshot();

  void setSnapshot(MorphologySnapshot snapshot);
}

package com.bitext.lucene.analysis.en.attributes;

import com.bitext.lucene.analysis.en.model.MorphologySnapshot;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public final class MorphologyAttributeImpl extends AttributeImpl implements MorphologyAttribute {
  private MorphologySnapshot snapshot;

  public MorphologySnapshot getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(MorphologySnapshot value) {
    snapshot = value;
  }

  @Override
  public void clear() {
    snapshot = null;
  }

  @Override
  public void copyTo(AttributeImpl target) {
    ((MorphologyAttribute) target).setSnapshot(snapshot);
  }

  @Override
  public void reflectWith(AttributeReflector r) {
    r.reflect(MorphologyAttribute.class, "snapshot", snapshot);
  }
}

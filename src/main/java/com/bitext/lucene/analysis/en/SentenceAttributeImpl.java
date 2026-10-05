package com.bitext.lucene.analysis.en;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public final class SentenceAttributeImpl extends AttributeImpl implements SentenceAttribute {
  private boolean present;
  private int id;

  public boolean hasSentenceMetadata() {
    return present;
  }

  public void setSentence(int value) {
    present = true;
    id = value;
  }

  public void clearSentence() {
    present = false;
    id = 0;
  }

  public int sentenceId() {
    if (!present) throw new IllegalStateException("no sentence metadata");
    return id;
  }

  @Override
  public void clear() {
    clearSentence();
  }

  @Override
  public void copyTo(AttributeImpl target) {
    SentenceAttribute t = (SentenceAttribute) target;
    if (present) t.setSentence(id);
    else t.clearSentence();
  }

  @Override
  public void reflectWith(AttributeReflector r) {
    r.reflect(SentenceAttribute.class, "sentenceId", present ? id : null);
  }
}

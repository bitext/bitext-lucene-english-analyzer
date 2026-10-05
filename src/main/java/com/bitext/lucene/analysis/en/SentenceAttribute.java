package com.bitext.lucene.analysis.en;

import org.apache.lucene.util.Attribute;

/** Genuine upstream sentence annotation for SUPPLIED_SENTENCES. */
public interface SentenceAttribute extends Attribute {
  boolean hasSentenceMetadata();

  void setSentence(int id);

  void clearSentence();

  int sentenceId();
}
